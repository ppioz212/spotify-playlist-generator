package com.asuresh.spotifyplaylistcompiler;

import com.google.gson.Gson;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.asuresh.spotifyplaylistcompiler.Utils.mergeToUniqueList;

@RestController
public class SpotifyController {
    public static final MediaType JSON = MediaType.get("application/json");
    static final private String ACCESS_TOKEN_FILENAME = "accessTokenInfo.txt";
    static String clientID = "1dbfd19797084691bbd011cab62cb6a6";
    static String secretClientID = "56b83ad8f2e8441288feb994cec8d231";
    static String redirectUri = "http://localhost:3000";




    @PostMapping("/getAccessToken")
    public SpotifyToken getAccessToken(@org.springframework.web.bind.annotation.RequestBody String generatedCode) {
        return generateToken(generatedCode);
    }
    //comment example
    @PostMapping("/generateNewPlaylist")
    public String generateNewPlaylist(@org.springframework.web.bind.annotation.RequestBody PlaylistDTO playlistObject, @RequestHeader("Authorization") String accessToken) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Gson gson = new Gson();

        List<String> playlistIds = compilePlaylists(accessToken, getUserId(accessToken), playlistObject);
//        System.out.println(playlistIds.size());

        List<String> playlistTrackIds = compilePlaylistTrackIds(playlistIds, accessToken);
//        System.out.println("The number of tracks without duplicates is: " + playlistTrackIds.size());
        List<String> savedSongsTrackIds = compileUserSavedSongIds(accessToken);
        List<String> finalTrackIdsToAdd = mergeToUniqueList(savedSongsTrackIds, playlistTrackIds);

        String newPlaylistId = createNewPlaylist(accessToken, playlistObject.getNameOfPlaylist(), "Combining music from all playlists created by user and liked songs");
//        System.out.println(newPlaylistId);

        addTrackItemsToNewPlaylist(accessToken, newPlaylistId, finalTrackIdsToAdd);
        return newPlaylistId;
    }

    public static void addTrackItemsToNewPlaylist(String accessToken, String newPlaylistId, List<String> trackIdsToAdd) throws IOException {
        OkHttpClient client = new OkHttpClient();

        int numberOfTimesToAddTracks = trackIdsToAdd.size() / 98 + 1;
        int j = 0;
        for (int i = 0; i < numberOfTimesToAddTracks; i++) {
            JSONArray trackListURIs = new JSONArray();
            for (; j < trackIdsToAdd.size(); j++) {
                trackListURIs.put("spotify:track:" + trackIdsToAdd.get(j));
                if (j % 98 == 0 && j != 0) {
                    j++;
                    break;
                }
            }
            JSONObject finalTrackListURis = new JSONObject();
            finalTrackListURis.put("uris", trackListURIs);

            RequestBody body = RequestBody.create(String.valueOf(finalTrackListURis), JSON);
            Request request = new Request.Builder()
                    .url("https://api.spotify.com/v1/playlists/" + newPlaylistId + "/tracks")
                    .post(body)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                assert response.body() != null;
            }
        }
    }

    public static String createNewPlaylist(String accessToken, String newPlaylistName, String newPlaylistDescription) throws IOException {
        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create("{\n    \"name\": \"" + newPlaylistName + "\",\n    \"description\": \"" + newPlaylistDescription + "\",\n    \"public\": false\n}", JSON);
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me/playlists")
                .post(body)
                .header("Authorization",  "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            assert response.body() != null;
            String jsonOutput = response.body().string();
            JSONObject obj = new JSONObject(jsonOutput);
            return obj.getString("id");
        }
    }
    public static List<String> compileUserSavedSongIds(String accessToken) throws IOException {
        OkHttpClient client = new OkHttpClient();

        List<String> savedSongIds = new ArrayList<>();
        String savedSongsUrl = "https://api.spotify.com/v1/me/tracks?limit=50";
        boolean shouldRunRequestAgain = true;
        while (shouldRunRequestAgain) {
            Request request = new Request.Builder()
                    .url(savedSongsUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                assert response.body() != null;
                String jsonOutput = response.body().string();
                JSONObject obj = new JSONObject(jsonOutput);
                if (obj.isNull("next")) {
                    shouldRunRequestAgain = false;
                } else {
                    savedSongsUrl = obj.getString("next");
                }
                JSONArray savedSongsItems = obj.getJSONArray("items");
                for (int i = 0; i < savedSongsItems.length(); i++) {
                    JSONObject playlistItemsData = savedSongsItems.getJSONObject(i);
                    if (!(savedSongIds.contains(playlistItemsData.getJSONObject("track").getString("id")))) {
                        savedSongIds.add(playlistItemsData.getJSONObject("track").getString("id"));
                    }
                }
            }
        }
        return savedSongIds;
    }
    public static List<String> compilePlaylistTrackIds(List<String> playlistIds, String accessToken) throws IOException {
        OkHttpClient client = new OkHttpClient();

        List<String> playlistTracks = new ArrayList<>();
        for (String playlistID : playlistIds) {
            String playlistTracksUrl = "https://api.spotify.com/v1/playlists/" + playlistID + "/tracks?limit=50";
            boolean shouldRunRequestAgain = true;
            while (shouldRunRequestAgain) {
                Request request = new Request.Builder()
                        .url(playlistTracksUrl)
                        .header("Authorization", "Bearer " + accessToken)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                    assert response.body() != null;
                    String jsonOutput = response.body().string();
                    JSONObject obj = new JSONObject(jsonOutput);
                    if (obj.isNull("next")) {
                        shouldRunRequestAgain = false;
                    } else {
                        playlistTracksUrl = obj.getString("next");
                    }
                    JSONArray playlistItems = obj.getJSONArray("items");
                    for (int i = 0; i < playlistItems.length(); i++) {
                        JSONObject playlistItemsData = playlistItems.getJSONObject(i);
                        if (playlistItemsData.getJSONObject("track").isNull("id") ||
                                (playlistItemsData.keySet().contains("episode"))) {
                            continue;
                        }
                        if (!(playlistTracks.contains(playlistItemsData.getJSONObject("track").getString("id")))) {
                            playlistTracks.add(playlistItemsData.getJSONObject("track").getString("id"));
                        }
                    }
                }
            }
        }
        return playlistTracks;
    }
    public static List<String> compilePlaylists(String accessToken, String userId, PlaylistDTO playlistObject) throws IOException {
        OkHttpClient client = new OkHttpClient();

        List<String> userOwnedPlaylistIds = new ArrayList<>();
        boolean shouldRunRequestAgain = true;
        String playlistUrl = "https://api.spotify.com/v1/me/playlists?limit=50";
        while (shouldRunRequestAgain) {
            Request request = new Request.Builder()
                    .url(playlistUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                assert response.body() != null;
                String jsonOutput = response.body().string();

                JSONObject obj = new JSONObject(jsonOutput);
                if (obj.isNull("next")) {
                    shouldRunRequestAgain = false;
                } else {
                    playlistUrl = obj.getString("next");
                }
                System.out.println("The total number of playlists avaiable is: " + obj.getInt("total"));
                JSONArray playlistItems = obj.getJSONArray("items");
                int totalTracks = 0;
                for (int i = 0; i < playlistItems.length(); i++) {
                    JSONObject playlistData = playlistItems.getJSONObject(i);
                    JSONObject playlistOwner = playlistData.getJSONObject("owner");
                    if (playlistObject.getTypeOfPlaylist().equals(PlaylistTypeEnum.ALL_USER_CREATED)){
                        if (playlistOwner.getString("id").equals(userId)) {
                            userOwnedPlaylistIds.add(playlistData.getString("id"));
                        }
                    } else if (playlistObject.getTypeOfPlaylist().equals(PlaylistTypeEnum.ALL_USER_OWNED)) {
                        userOwnedPlaylistIds.add(playlistData.getString("id"));
                    }

                    totalTracks += playlistData.getJSONObject("tracks").getInt("total");
                }
                System.out.println("Raw number of tracks = " + totalTracks);

            }
        }
        return userOwnedPlaylistIds;
    }

    public static String getUserId(String accessToken) throws IOException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        String jsonOutput;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            assert response.body() != null;
            jsonOutput = response.body().string();
        }
        return new JSONObject(jsonOutput).getString("id");
    }

    public static SpotifyToken generateToken(String generatedCode) {
        OkHttpClient client = new OkHttpClient();
        Gson gson = new Gson();

        String authHeader = clientID + ":" + secretClientID;
        String encodedString = Base64.getEncoder().encodeToString(authHeader.getBytes());

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", generatedCode)
                .add("redirect_uri", redirectUri)
                .build();

        Request request = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + encodedString)
                .build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            return gson.fromJson(response.body().string(), SpotifyToken.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}