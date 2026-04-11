package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class DemoMusicCatalog {

    private final Clock clock;

    public DemoMusicCatalog(Clock clock) {
        this.clock = clock;
    }

    public List<Track> searchTracks(String query, int limit) {
        Set<String> tokens = tokenize(query);
        Stream<DemoTrack> stream = catalog().stream();
        if (!tokens.isEmpty()) {
            stream = stream
                    .map(track -> Map.entry(track, score(track, tokens)))
                    .filter(entry -> entry.getValue() > 0)
                    .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                    .map(Map.Entry::getKey);
        }

        LocalDateTime now = now();
        return stream
                .limit(limit)
                .map(track -> track.toDomainTrack(now))
                .toList();
    }

    public Optional<Track> findTrackById(String trackId) {
        return catalog().stream()
                .filter(track -> track.trackId().equals(trackId))
                .findFirst()
                .map(track -> track.toDomainTrack(now()));
    }

    public Optional<Artist> findArtistById(String artistId) {
        DemoArtist artist = artists().get(artistId);
        return artist == null
                ? Optional.empty()
                : Optional.of(artist.toDomainArtist(now()));
    }

    private int score(DemoTrack track, Set<String> tokens) {
        int score = 0;
        for (String token : tokens) {
            score += scoreMatches(token, track.matchFields(), 3);
            score += scoreMatches(token, track.tags(), 5);
        }
        return score;
    }

    private Set<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }

        String normalized = query
                .toLowerCase(Locale.ROOT)
                .replace("，", " ")
                .replace("。", " ")
                .replace(",", " ")
                .replace(".", " ")
                .replace("给我", " ")
                .replace("来点", " ")
                .replace("来一些", " ")
                .replace("来一首", " ")
                .replace("推荐", " ")
                .replace("播放", " ")
                .replace("随机", " ")
                .replace("先不要播放", " ")
                .replace("不要播放", " ")
                .replace("歌单", " ")
                .replace("音乐", " ");

        return Stream.of(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private List<DemoTrack> catalog() {
        return List.of(
                new DemoTrack(
                        "demo-track-1",
                        "粤语轻松精选",
                        "demo-artist-eason",
                        "AgentMusic Demo Mix",
                        "demo-album-1",
                        180000,
                        "https://p.scdn.co/mp3-preview/8dd8fbb1721d4964028ad362a80ad3ae2422f547?cid=6d083ea30aaa46428fbf1590d31f6750",
                        "https://i.scdn.co/image/ab67616d0000b2731d26b3e7ea803059a6e4ffea",
                        Set.of("粤语", "轻松", "推荐", "通勤", "晚间")
                ),
                new DemoTrack(
                        "demo-track-2",
                        "夜晚慢歌电台",
                        "demo-artist-jj",
                        "City Night Session",
                        "demo-album-2",
                        204000,
                        null,
                        "https://i.scdn.co/image/ab67616d0000b2731d26b3e7ea803059a6e4ffea",
                        Set.of("慢歌", "夜晚", "放松", "抒情")
                ),
                new DemoTrack(
                        "demo-track-3",
                        "学习专注模式",
                        "demo-artist-hins",
                        "Focus Playlist",
                        "demo-album-3",
                        196000,
                        null,
                        "https://i.scdn.co/image/ab67616d0000b2731d26b3e7ea803059a6e4ffea",
                        Set.of("学习", "专注", "纯净", "舒缓")
                ),
                new DemoTrack(
                        "demo-track-4",
                        "陈奕迅风格推荐",
                        "demo-artist-eason",
                        "Canto Moodboard",
                        "demo-album-4",
                        212000,
                        null,
                        "https://i.scdn.co/image/ab67616d0000b2731d26b3e7ea803059a6e4ffea",
                        Set.of("陈奕迅", "粤语", "流行", "经典")
                ),
                new DemoTrack(
                        "demo-track-5",
                        "随机活力歌单",
                        "demo-artist-gem",
                        "Shuffle Energy",
                        "demo-album-5",
                        188000,
                        null,
                        "https://i.scdn.co/image/ab67616d0000b2731d26b3e7ea803059a6e4ffea",
                        Set.of("随机", "活力", "流行", "运动")
                )
        );
    }

    private int scoreMatches(String token, Set<String> values, int matchedValue) {
        int score = 0;
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        for (String value : values) {
            String normalizedValue = value.toLowerCase(Locale.ROOT);
            if (normalizedValue.equals(normalizedToken)) {
                score += matchedValue + 2;
                continue;
            }
            if (normalizedValue.contains(normalizedToken) || normalizedToken.contains(normalizedValue)) {
                score += matchedValue;
            }
        }
        return score;
    }

    private Map<String, DemoArtist> artists() {
        Map<String, DemoArtist> artists = new LinkedHashMap<>();
        artists.put("demo-artist-eason", new DemoArtist(
                "demo-artist-eason",
                "陈奕迅",
                "香港流行音乐代表歌手，适合作为粤语、抒情、经典推荐的演示数据。",
                "https://i.scdn.co/image/ab6761610000e5eb2db8426985f3fd5aa5ea4b24",
                1200000
        ));
        artists.put("demo-artist-jj", new DemoArtist(
                "demo-artist-jj",
                "林俊杰",
                "适合作为夜晚慢歌、流行抒情和国语推荐的演示数据。",
                "https://i.scdn.co/image/ab6761610000e5eb17b6feda0b97c11f24e8e0f2",
                980000
        ));
        artists.put("demo-artist-hins", new DemoArtist(
                "demo-artist-hins",
                "张敬轩",
                "适合作为专注、舒缓和粤语情绪推荐的演示数据。",
                "https://i.scdn.co/image/ab6761610000e5ebf5bf85de1b48f87005a3f381",
                540000
        ));
        artists.put("demo-artist-gem", new DemoArtist(
                "demo-artist-gem",
                "G.E.M.",
                "适合作为活力流行与高辨识度女声推荐的演示数据。",
                "https://i.scdn.co/image/ab6761610000e5eb2d3e9be3b789a501d0689be1",
                860000
        ));
        return artists;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }

    private record DemoTrack(
            String trackId,
            String title,
            String artistId,
            String albumName,
            String albumId,
            Integer durationMs,
            String previewUrl,
            String albumImageUrl,
            Set<String> tags
    ) {
        Track toDomainTrack(LocalDateTime now) {
            return new Track(trackId, title, artistId, albumName, albumId, durationMs, previewUrl, albumImageUrl, now, now);
        }

        Set<String> matchFields() {
            return Set.of(title.toLowerCase(Locale.ROOT), artistId.toLowerCase(Locale.ROOT), albumName.toLowerCase(Locale.ROOT));
        }
    }

    private record DemoArtist(
            String artistId,
            String name,
            String bio,
            String imageUrl,
            Integer followers
    ) {
        Artist toDomainArtist(LocalDateTime now) {
            return new Artist(artistId, name, bio, imageUrl, followers, now);
        }
    }
}
