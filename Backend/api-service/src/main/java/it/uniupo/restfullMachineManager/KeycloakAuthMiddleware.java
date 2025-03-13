package it.uniupo.restfullMachineManager;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import spark.Request;
import spark.Response;

import javax.net.ssl.SSLContext;

public class KeycloakAuthMiddleware {
    private static final String KEYCLOAK_URL = System.getenv("KEYCLOAK_URL");
    private static final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    public static boolean authenticate(Request request, Response response) {
        try {
            String authHeader = request.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.status(401);
                return false;
            }

            String token = authHeader.substring(7);
            DecodedJWT jwt = JWT.decode(token);
            RSAPublicKey publicKey = getPublicKey(jwt.getKeyId());

            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            JWT.require(algorithm).withIssuer(KEYCLOAK_URL).build().verify(token);

            request.attribute("user", jwt.getClaim("preferred_username").asString());
            request.attribute("roles", jwt.getClaim("realm_access").asMap().get("roles"));

            return true;
        } catch (Exception e) {
            response.status(401);
            return false;
        }
    }

    public static RSAPublicKey getPublicKey(String kid) throws Exception {
        if (keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        try (CloseableHttpClient httpClient = createHttpClientWithCustomCACert("/app/certs/ca.crt")) {

            // Create a GET request for the JWKS endpoint
            HttpGet httpGet = new HttpGet(KEYCLOAK_URL + "/protocol/openid-connect/certs");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                // Check the response status
                if (response.getCode() != 200) {
                    throw new IOException("Failed to fetch JWKS: " + response.getCode());
                }
                // Extract the response content (body) as a String
                String jsonResponse = EntityUtils.toString(response.getEntity());

                // Parse the response content into a JsonObject
                JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();

                for (var key : json.getAsJsonArray("keys")) {
                    JsonObject keyObj = key.getAsJsonObject();
                    if (kid.equals(keyObj.get("kid").getAsString())) {
                        RSAPublicKey publicKey = generateRSAPublicKey(
                                keyObj.get("n").getAsString(), keyObj.get("e").getAsString()
                        );
                        keyCache.put(kid, publicKey);
                        return publicKey;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Failed to fetch JWKS", e);
            }
        }
        throw new RuntimeException("Key not found in JWKS");
    }

    private static RSAPublicKey generateRSAPublicKey(String n, String e) throws Exception {
        byte[] modulusBytes = Base64.getUrlDecoder().decode(n);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(e);
        return createPublicKey(modulusBytes, exponentBytes);
    }

    public static RSAPublicKey createPublicKey(byte[] modulus, byte[] exponent) throws Exception {
        BigInteger mod = new BigInteger(1, modulus);
        BigInteger exp = new BigInteger(1, exponent);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(mod, exp);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    public static CloseableHttpClient createHttpClientWithCustomCACert(String caCertPath) throws Exception {
        // Load the CA certificate
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        FileInputStream certInputStream = new FileInputStream(caCertPath);
        X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(certInputStream);

        // Create a KeyStore and load the CA certificate
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null); // Create an empty keystore
        keyStore.setCertificateEntry("ca", caCert);

        // Create an SSLContext with the custom CA certificate
        SSLContext sslContext = SSLContexts
                .custom()
                .loadTrustMaterial(keyStore, null)
                .build();

        SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder
                .create()
                .setSslContext(sslContext)
                .build();

        Registry<ConnectionSocketFactory> customRegistry = RegistryBuilder
                .<ConnectionSocketFactory>create()
                .register("https", sslSocketFactory)
                .register("http", new PlainConnectionSocketFactory())
                .build();

        // Create an HttpClient with the custom SSLContext
        return HttpClients.custom()
                .setConnectionManager(new BasicHttpClientConnectionManager( customRegistry))
                .build();
    }
}
