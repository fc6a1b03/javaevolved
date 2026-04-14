///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.3
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3

import module java.base;
import java.net.http.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * Post the next tweet from the social queue to Twitter/X.
 *
 * Reads state from social/state.yaml, posts via Twitter API v2,
 * and updates state only after confirmed API success.
 *
 * Required environment variables:
 *   TWITTER_CONSUMER_KEY, TWITTER_CONSUMER_KEY_SECRET,
 *   TWITTER_ACCESS_TOKEN, TWITTER_ACCESS_TOKEN_SECRET
 *
 * Options:
 *   --dry-run   Print the tweet without posting
 */

static final String SOCIAL_DIR = "social";
static final String QUEUE_FILE = SOCIAL_DIR + "/queue.txt";
static final String TWEETS_FILE = SOCIAL_DIR + "/tweets.yaml";
static final String STATE_FILE = SOCIAL_DIR + "/state.yaml";
static final String TWITTER_API_URL = "https://api.twitter.com/2/tweets";

static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
static final ObjectMapper YAML_WRITER = new ObjectMapper(
    new YAMLFactory()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
);
static final ObjectMapper JSON_MAPPER = new ObjectMapper();

void main(String... args) throws Exception {
    boolean dryRun = List.of(args).contains("--dry-run");

    // 1. Load queue, tweets, state
    var queue = loadQueue();
    var tweets = loadTweets();
    var state = loadState();

    int currentIndex = ((Number) state.get("currentIndex")).intValue();
    System.out.println("Queue has " + queue.size() + " entries, current index: " + currentIndex);

    // 2. Check if queue is exhausted
    if (currentIndex > queue.size()) {
        System.out.println("Queue exhausted — reshuffle needed.");
        System.out.println("Run: jbang html-generators/generatesocialqueue.java --reshuffle");
        System.exit(1);
    }

    // 3. Get the current pattern key and tweet text
    var key = queue.get(currentIndex - 1); // 1-based index
    var tweetText = tweets.get(key);

    if (tweetText == null) {
        System.err.println("ERROR: No tweet text found for key: " + key);
        System.err.println("Regenerate tweets: jbang html-generators/generatesocialqueue.java");
        System.exit(1);
    }

    System.out.println("Pattern: " + key);
    System.out.println("Tweet (" + tweetText.length() + " chars):");
    System.out.println("---");
    System.out.println(tweetText);
    System.out.println("---");

    if (dryRun) {
        System.out.println("DRY RUN — not posting.");
        return;
    }

    // 4. Read Twitter credentials from environment
    var consumerKey = requireEnv("TWITTER_CONSUMER_KEY");
    var consumerSecret = requireEnv("TWITTER_CONSUMER_KEY_SECRET");
    var accessToken = requireEnv("TWITTER_ACCESS_TOKEN");
    var accessTokenSecret = requireEnv("TWITTER_ACCESS_TOKEN_SECRET");

    // 5. Post to Twitter
    var tweetId = postTweet(tweetText, consumerKey, consumerSecret, accessToken, accessTokenSecret);
    System.out.println("Posted! Tweet ID: " + tweetId);

    // 6. Update state only after success
    state.put("currentIndex", currentIndex + 1);
    state.put("lastPostedKey", key);
    state.put("lastTweetId", tweetId);
    state.put("lastPostedAt", java.time.Instant.now().toString());
    YAML_WRITER.writerWithDefaultPrettyPrinter().writeValue(Path.of(STATE_FILE).toFile(), state);
    System.out.println("State updated: index now " + (currentIndex + 1));
}

// --- Twitter API v2 with OAuth 1.0a ---

String postTweet(String text, String consumerKey, String consumerSecret,
                 String token, String tokenSecret) throws Exception {
    var method = "POST";
    var url = TWITTER_API_URL;

    // OAuth parameters
    var oauthParams = new TreeMap<String, String>();
    oauthParams.put("oauth_consumer_key", consumerKey);
    oauthParams.put("oauth_nonce", generateNonce());
    oauthParams.put("oauth_signature_method", "HMAC-SHA1");
    oauthParams.put("oauth_timestamp", String.valueOf(Instant.now().getEpochSecond()));
    oauthParams.put("oauth_token", token);
    oauthParams.put("oauth_version", "1.0");

    // Build signature base string (no body params for JSON content type)
    var paramString = oauthParams.entrySet().stream()
        .map(e -> percentEncode(e.getKey()) + "=" + percentEncode(e.getValue()))
        .collect(Collectors.joining("&"));

    var baseString = method + "&" + percentEncode(url) + "&" + percentEncode(paramString);
    var signingKey = percentEncode(consumerSecret) + "&" + percentEncode(tokenSecret);

    var signature = hmacSha1(signingKey, baseString);
    oauthParams.put("oauth_signature", signature);

    // Build Authorization header
    var authHeader = "OAuth " + oauthParams.entrySet().stream()
        .map(e -> percentEncode(e.getKey()) + "=\"" + percentEncode(e.getValue()) + "\"")
        .collect(Collectors.joining(", "));

    // Build JSON body
    var bodyMap = Map.of("text", text);
    var body = JSON_MAPPER.writeValueAsString(bodyMap);

    // Send request
    var client = HttpClient.newHttpClient();
    var request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", authHeader)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 201) {
        System.err.println("Twitter API error (HTTP " + response.statusCode() + "):");
        System.err.println(response.body());
        System.exit(1);
    }

    var responseNode = JSON_MAPPER.readTree(response.body());
    return responseNode.path("data").path("id").asText();
}

String generateNonce() {
    var bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
}

String hmacSha1(String key, String data) throws Exception {
    var mac = javax.crypto.Mac.getInstance("HmacSHA1");
    mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
    var raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(raw);
}

String percentEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~");
}

// --- File helpers ---

List<String> loadQueue() throws Exception {
    var lines = Files.readAllLines(Path.of(QUEUE_FILE)).stream()
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
    if (lines.isEmpty()) {
        System.err.println("ERROR: " + QUEUE_FILE + " is empty. Run the queue generator first.");
        System.exit(1);
    }
    return lines;
}

@SuppressWarnings("unchecked")
Map<String, String> loadTweets() throws Exception {
    return YAML_MAPPER.readValue(Path.of(TWEETS_FILE).toFile(), LinkedHashMap.class);
}

@SuppressWarnings("unchecked")
Map<String, Object> loadState() throws Exception {
    return YAML_MAPPER.readValue(Path.of(STATE_FILE).toFile(), LinkedHashMap.class);
}

String requireEnv(String name) {
    var value = System.getenv(name);
    if (value == null || value.isBlank()) {
        System.err.println("ERROR: Missing environment variable: " + name);
        System.exit(1);
    }
    return value;
}
