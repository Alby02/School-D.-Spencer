package it.uniupo.restfullMachineManager;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//import org.apache.hc.client5.http.fluent.Request;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import spark.Request;
import spark.Response;

public class KeycloakAuthMiddleware {
    private static final String KEYCLOAK_URL = System.getenv("KEYCLOAK_URL");
    private static final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    public static boolean authenticate(Request request, Response response) {
        try {
            System.out.println(request.toString());
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

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

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
}
