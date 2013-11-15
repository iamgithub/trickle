package com.spotify.trickle.example;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.spotify.trickle.Graph;
import com.spotify.trickle.Name;
import com.spotify.trickle.Node2;
import com.spotify.trickle.Node3;
import com.spotify.trickle.Trickle;

import java.util.List;

public class PSearchView {
  private static final Name<RequestContext> CONTEXT = Name.named("context", RequestContext.class);
  private static final Name<Message> REQUEST = Name.named("request", Message.class);
  private static final Name<String> QUERY = Name.named("query", String.class);

  private final Graph<AllData> graph;

  public PSearchView() {
    Node3<RequestContext, String, Message, Suggestions> getSuggestions = suggestionsNode();
    Node2<RequestContext, Suggestions, List<MetadataReply<Track>>> fetchTrackMetadata = trackMetaDataNode();
    Node2<RequestContext, Suggestions, List<Long>> fetchPlaylistFollowers = playlistFollowersNode();
    Node3<Suggestions, List<MetadataReply<Track>>, List<Long>, AllData> allData = combineItAllNode();

    graph = Trickle.graph(AllData.class)
        .inputs(CONTEXT, REQUEST, QUERY)
        .call(getSuggestions).with(CONTEXT, QUERY, REQUEST)
        .call(fetchTrackMetadata).with(CONTEXT, getSuggestions)
        .call(fetchPlaylistFollowers).with(CONTEXT, getSuggestions)
        .call(allData).with(getSuggestions, fetchTrackMetadata, fetchPlaylistFollowers)
        .output(allData);
  }

  public ListenableFuture<AllData> suggest(final RequestContext context,
                                           final Message request,
                                           String query) {
    return graph
        .bind(CONTEXT, context)
        .bind(REQUEST, request)
        .bind(QUERY, query)
        .run();
  }

  private Node3<RequestContext, String, Message, Suggestions> suggestionsNode() {
    return (context, query, request) -> getSuggestions(context, query, "track,album,artist,playlist", request.getParameter("country"));
  }

  public static ListenableFuture<Suggestions> getSuggestions(final RequestContext context,
                                                             String query, String suggestType, String country) {
    return Futures.transform(context.request(buildSuggestRequest(query, suggestType, country)), (AsyncFunction<? super Message,? extends Suggestions>) reply -> null);
  }

  private static String buildSuggestRequest(String query,
                                            String type, String country) {
    String hermesURL = "hm://searchsuggest/suggest/";
    hermesURL += query;
    hermesURL += "?country=" + country;
    if (type != null) {
      hermesURL += "&search-type=" + type;
    }

    return hermesURL;
  }
  private Node2<RequestContext, Suggestions, List<MetadataReply<Track>>> trackMetaDataNode() {
    return (context, suggestions) -> {
      List<String> gids = Lists.transform(suggestions.getTrackList(), track -> Util.hex(track.getGid()));
      return MetadataClient.getMetadata(context, MetadataType.TRACK, gids);
    };
  }

  private Node2<RequestContext, Suggestions, List<Long>> playlistFollowersNode() {
    return (context, suggestions) -> {
      List<String> uris = Lists.transform(suggestions.getPlaylistList(), Playlist::getUri);
      return PopcountClient.getFollowerCount(context, uris);
    };
  }

  private Node3<Suggestions, List<MetadataReply<Track>>, List<Long>, AllData> combineItAllNode() {
    return (suggestions, metadataReplies, followers) -> {
      EntityData.Builder<Album> albumDataBuilder = new EntityData.Builder<>();
      albumDataBuilder.withTotal(suggestions.getAlbumCount());
      for (Album album : suggestions.getAlbumList()) {
        albumDataBuilder.withHit(Album.fromSuggestAlbum(album));
      }
      final EntityData<Album> albums = albumDataBuilder.build();

      EntityData.Builder<Artist> artistDataBuilder = new EntityData.Builder<>();
      artistDataBuilder.withTotal(suggestions.getArtistCount());
      for (Artist artist : suggestions.getArtistList()) {
        artistDataBuilder.withHit(Artist.fromSuggestArtist(artist));
      }
      final EntityData<Artist> artists = artistDataBuilder.build();

      EntityData.Builder<Track> trackDataBuilder = new EntityData.Builder<>();
      trackDataBuilder.withTotal(suggestions.getTrackCount());
      for (ListZip.Pair<MetadataReply<Track>, Track> pair : ListZip.zip(metadataReplies, suggestions.getTrackList())) {
        if (pair.first != null) {
          trackDataBuilder.withHit(Track.fromDecoratedSuggestTrack(pair.first.getData(), pair.second));
        }
      }
      final EntityData<Track> tracks = trackDataBuilder.build();

      EntityData.Builder<Playlist> playlistDataBuilder = new EntityData.Builder<>();
      playlistDataBuilder.withTotal(suggestions.getPlaylistCount());
      for (ListZip.Pair<Playlist, Long> pair : ListZip.zip(suggestions.getPlaylistList(), followers)) {
        playlistDataBuilder.withHit(Playlist.fromSuggestPlaylist(pair.first, pair.second));

      }
      final EntityData<Playlist> playlists = playlistDataBuilder.build();

      AllData.Builder allDataBuilder = new AllData.Builder();
      allDataBuilder.withAlbums(albums);
      allDataBuilder.withArtists(artists);
      allDataBuilder.withPlaylists(playlists);
      allDataBuilder.withTracks(tracks);
      return Futures.immediateFuture(allDataBuilder.build());
    };
  }




  private static class RequestContext {
    public ListenableFuture<Message> request(String s) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class Suggestions {
    public List<Playlist> getPlaylistList() {
      return null;
    }

    public int getPlaylistCount() {
      return 0;  //To change body of created methods use File | Settings | File Templates.
    }

    public Iterable<? extends Artist> getArtistList() {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }

    public int getArtistCount() {
      return 0;  //To change body of created methods use File | Settings | File Templates.
    }

    public Iterable<? extends Album> getAlbumList() {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }

    public int getAlbumCount() {
      return 0;  //To change body of created methods use File | Settings | File Templates.
    }

    public List<Track> getTrackList() {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }

    public int getTrackCount() {
      return 0;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class Message {
    public String getParameter(String country) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class AllData {
    private static class Builder {

      public void withAlbums(EntityData<Album> albums) {
        //To change body of created methods use File | Settings | File Templates.
      }

      public void withArtists(EntityData<Artist> artists) {
        //To change body of created methods use File | Settings | File Templates.
      }

      public void withPlaylists(EntityData<Playlist> playlists) {
        //To change body of created methods use File | Settings | File Templates.
      }

      public void withTracks(EntityData<Track> tracks) {
        //To change body of created methods use File | Settings | File Templates.
      }

      public AllData build() {
        return null;  //To change body of created methods use File | Settings | File Templates.
      }
    }
  }

  private static class Track {
    public static Object fromDecoratedSuggestTrack(Object data, Track second) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }

    public String getGid() {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class MetadataReply<T> {
    public Object getData() {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class Album {
    public static Object fromSuggestAlbum(Album album) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class EntityData<T> {
    private static class Builder<T> {

      public EntityData<T> build() {
        return null;  //To change body of created methods use File | Settings | File Templates.
      }

      public void withHit(Object dummy) {
        //To change body of created methods use File | Settings | File Templates.
      }

      public void withTotal(int playlistCount) {
        //To change body of created methods use File | Settings | File Templates.
      }
    }
  }

  private static class Profile {
  }

  private static class Playlist {
    public static Playlist fromSuggestPlaylist(Playlist first, Long second) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }

    public String getUri() {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class ListZip {
    public static <L, R> Iterable<Pair<L, R>> zip(List<L> left, List<R> right) {
      return null;
    }

    private static class Pair<L, R> {
      public L first;
      public R second;
    }
  }

  private static class Artist {
    public static Object fromSuggestArtist(Artist artist) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class MetadataType {
    public static final Object TRACK = new Object();
  }

  private static class MetadataClient {
    public static ListenableFuture<List<MetadataReply<Track>>> getMetadata(RequestContext context, Object track, List<String> gids) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class Util {
    public static String hex(String gid) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }

  private static class PopcountClient {
    public static ListenableFuture<List<Long>> getFollowerCount(RequestContext context, List<String> uris) {
      return null;  //To change body of created methods use File | Settings | File Templates.
    }
  }
}