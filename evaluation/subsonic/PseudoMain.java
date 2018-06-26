package net.sourceforge.subsonic;

import static edu.washington.cse.servlet.Util.nondetBool;
import static edu.washington.cse.servlet.Util.nondetInt;

import java.io.IOException;
import java.util.Arrays;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.ehcache.Ehcache;
import net.sourceforge.subsonic.PseudoMain.DummyAcegiFilter;
import net.sourceforge.subsonic.PseudoMain.SubsonicModel;
import net.sourceforge.subsonic.controller.AdvancedSettingsController;
import net.sourceforge.subsonic.controller.AllmusicController;
import net.sourceforge.subsonic.controller.AvatarController;
import net.sourceforge.subsonic.controller.AvatarUploadController;
import net.sourceforge.subsonic.controller.ChangeCoverArtController;
import net.sourceforge.subsonic.controller.CoverArtController;
import net.sourceforge.subsonic.controller.DBController;
import net.sourceforge.subsonic.controller.DLNASettingsController;
import net.sourceforge.subsonic.controller.DownloadController;
import net.sourceforge.subsonic.controller.EditTagsController;
import net.sourceforge.subsonic.controller.ExternalPlayerController;
import net.sourceforge.subsonic.controller.GeneralSettingsController;
import net.sourceforge.subsonic.controller.HLSController;
import net.sourceforge.subsonic.controller.HelpController;
import net.sourceforge.subsonic.controller.HomeController;
import net.sourceforge.subsonic.controller.ImportPlaylistController;
import net.sourceforge.subsonic.controller.InternetRadioSettingsController;
import net.sourceforge.subsonic.controller.LeftController;
import net.sourceforge.subsonic.controller.LyricsController;
import net.sourceforge.subsonic.controller.M3UController;
import net.sourceforge.subsonic.controller.MainController;
import net.sourceforge.subsonic.controller.MoreController;
import net.sourceforge.subsonic.controller.MultiController;
import net.sourceforge.subsonic.controller.MusicFolderSettingsController;
import net.sourceforge.subsonic.controller.NetworkSettingsController;
import net.sourceforge.subsonic.controller.NowPlayingController;
import net.sourceforge.subsonic.controller.PasswordSettingsController;
import net.sourceforge.subsonic.controller.PersonalSettingsController;
import net.sourceforge.subsonic.controller.PlayQueueController;
import net.sourceforge.subsonic.controller.PlayerSettingsController;
import net.sourceforge.subsonic.controller.PlaylistController;
import net.sourceforge.subsonic.controller.PlaylistsController;
import net.sourceforge.subsonic.controller.PodcastController;
import net.sourceforge.subsonic.controller.PodcastReceiverAdminController;
import net.sourceforge.subsonic.controller.PodcastReceiverController;
import net.sourceforge.subsonic.controller.PodcastSettingsController;
import net.sourceforge.subsonic.controller.PremiumController;
import net.sourceforge.subsonic.controller.ProxyController;
import net.sourceforge.subsonic.controller.RESTController;
import net.sourceforge.subsonic.controller.RandomPlayQueueController;
import net.sourceforge.subsonic.controller.RightController;
import net.sourceforge.subsonic.controller.SearchController;
import net.sourceforge.subsonic.controller.SetMusicFileInfoController;
import net.sourceforge.subsonic.controller.SetRatingController;
import net.sourceforge.subsonic.controller.SettingsController;
import net.sourceforge.subsonic.controller.ShareManagementController;
import net.sourceforge.subsonic.controller.ShareSettingsController;
import net.sourceforge.subsonic.controller.SonosSettingsController;
import net.sourceforge.subsonic.controller.StarredController;
import net.sourceforge.subsonic.controller.StatusChartController;
import net.sourceforge.subsonic.controller.StatusController;
import net.sourceforge.subsonic.controller.StreamController;
import net.sourceforge.subsonic.controller.TopController;
import net.sourceforge.subsonic.controller.TranscodingSettingsController;
import net.sourceforge.subsonic.controller.UploadController;
import net.sourceforge.subsonic.controller.UserChartController;
import net.sourceforge.subsonic.controller.UserSettingsController;
import net.sourceforge.subsonic.controller.VideoPlayerController;
import net.sourceforge.subsonic.controller.WapController;
import net.sourceforge.subsonic.ldap.SubsonicLdapBindAuthenticator;
import net.sourceforge.subsonic.service.SecurityService;

import org.acegisecurity.ui.logout.LogoutHandler;
import org.springframework.web.servlet.ModelAndView;

import edu.washington.cse.servlet.SimpleContext;
import edu.washington.cse.servlet.SimpleFilterConfig;
import edu.washington.cse.servlet.SimpleHttpRequest;
import edu.washington.cse.servlet.SimpleHttpResponse;
import edu.washington.cse.servlet.SimpleServletConfig;
import edu.washington.cse.servlet.SimpleSession;
import edu.washington.cse.servlet.jsp.SimpleJspFactory;

public class PseudoMain {
	public static class MyContext extends SimpleContext {
		@Override
		public void notifyRequestInitialized(final SimpleHttpRequest v0) {
		}

		@Override
		public void notifyRequestAttributeAdded(final SimpleHttpRequest v0, final String v1, final Object v2) {
		}

		@Override
		public void notifySessionAttributeReplaced(final SimpleSession v0, final String v1, final Object v2) {
		}

		@Override
		public void notifyContextDestroyed() {
		}

		@Override
		public void notifySessionAttributeRemoved(final SimpleSession v0, final String v1, final Object v2) {
		}

		@Override
		public void notifySessionDidActivate(final SimpleSession v0) {
		}

		@Override
		public void notifyRequestAttributeReplaced(final SimpleHttpRequest v0, final String v1, final Object v2) {
		}

		@Override
		public void notifyContextAttributeAdded(final String v0, final Object v1) {
		}

		@Override
		public void notifyRequestDestroyed(final SimpleHttpRequest v0) {
		}

		@Override
		public void notifySessionDestroyed(final SimpleSession v0) {
		}

		@Override
		public void notifyContextAttributeRemoved(final String v0, final Object v1) {
		}

		@Override
		public void notifyContextAttributeReplaced(final String v0, final Object v1) {
		}

		@Override
		public void notifyRequestAttributeRemoved(final SimpleHttpRequest v0, final String v1, final Object v2) {
		}

		@Override
		public void notifySessionAttributeAdded(final SimpleSession v0, final String v1, final Object v2) {
		}

		@Override
		public void notifyContextInitialized() {
		}

		@Override
		public void notifySessionCreated(final SimpleSession v0) {
		}

		@Override
		public void notifySessionWillPassivate(final SimpleSession v0) {
		}

		@Override
		public RequestDispatcher getRequestDispatcher(final String url) {
			if(nondetBool()) {
				return new Dispatcher_0((SubsonicModel) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_3((net.sourceforge.subsonic.WEB_002dINF.jsp.playlist_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_4((net.sourceforge.subsonic.WEB_002dINF.jsp.settingsHeader_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_5((net.sourceforge.subsonic.WEB_002dINF.jsp.db_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_6((net.sourceforge.subsonic.WEB_002dINF.jsp.transcodingSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_7((net.sourceforge.subsonic.WEB_002dINF.jsp.podcastReceiver_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_8((net.sourceforge.subsonic.WEB_002dINF.jsp.generalSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_9((net.sourceforge.subsonic.WEB_002dINF.jsp.videoMain_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_10((net.sourceforge.subsonic.WEB_002dINF.jsp.shareSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_11((net.sourceforge.subsonic.WEB_002dINF.jsp.playlists_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_12((net.sourceforge.subsonic.WEB_002dINF.jsp.right_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_13((net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayer_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_14((net.sourceforge.subsonic.WEB_002dINF.jsp.include_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_15((net.sourceforge.subsonic.WEB_002dINF.jsp.internetRadioSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_16((net.sourceforge.subsonic.WEB_002dINF.jsp.createShare_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_17((net.sourceforge.subsonic.WEB_002dINF.jsp.passwordSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_18((net.sourceforge.subsonic.WEB_002dINF.jsp.playQueueCast_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_19((net.sourceforge.subsonic.WEB_002dINF.jsp.status_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_20((net.sourceforge.subsonic.WEB_002dINF.jsp.top_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_21((net.sourceforge.subsonic.WEB_002dINF.jsp.avatarUploadResult_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_22((net.sourceforge.subsonic.WEB_002dINF.jsp.importPlaylist_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_23((net.sourceforge.subsonic.WEB_002dINF.jsp.playerSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_24((net.sourceforge.subsonic.WEB_002dINF.jsp.dlnaSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_25((net.sourceforge.subsonic.WEB_002dINF.jsp.helpToolTip_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_26((net.sourceforge.subsonic.WEB_002dINF.jsp.help_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_27((net.sourceforge.subsonic.WEB_002dINF.jsp.starred_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_28((net.sourceforge.subsonic.WEB_002dINF.jsp.podcastSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_29((net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayerCast_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_30((net.sourceforge.subsonic.WEB_002dINF.jsp.left_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_31((net.sourceforge.subsonic.WEB_002dINF.jsp.playButtons_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_32((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.playlist_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_33((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.settings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_34((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.search_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_35((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.head_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_36((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.loadPlaylist_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_37((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.searchResult_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_38((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.index_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_39((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.browse_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_40((net.sourceforge.subsonic.WEB_002dINF.jsp.rating_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_41((net.sourceforge.subsonic.WEB_002dINF.jsp.albumMain_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_42((net.sourceforge.subsonic.WEB_002dINF.jsp.notFound_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_43((net.sourceforge.subsonic.WEB_002dINF.jsp.upload_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_44((net.sourceforge.subsonic.WEB_002dINF.jsp.premium_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_45((net.sourceforge.subsonic.WEB_002dINF.jsp.userSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_46((net.sourceforge.subsonic.WEB_002dINF.jsp.changeCoverArt_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_47((net.sourceforge.subsonic.WEB_002dINF.jsp.musicFolderSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_48((net.sourceforge.subsonic.WEB_002dINF.jsp.rest.videoPlayer_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_49((net.sourceforge.subsonic.WEB_002dINF.jsp.editTags_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_50((net.sourceforge.subsonic.WEB_002dINF.jsp.licenseNotice_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_51((net.sourceforge.subsonic.WEB_002dINF.jsp.networkSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_52((net.sourceforge.subsonic.WEB_002dINF.jsp.viewSelector_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_53((net.sourceforge.subsonic.WEB_002dINF.jsp.search_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_54((net.sourceforge.subsonic.WEB_002dINF.jsp.home_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_55((net.sourceforge.subsonic.WEB_002dINF.jsp.externalPlayer_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_56((net.sourceforge.subsonic.WEB_002dINF.jsp.head_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_57((net.sourceforge.subsonic.WEB_002dINF.jsp.lyrics_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_58((net.sourceforge.subsonic.WEB_002dINF.jsp.coverArt_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_59((net.sourceforge.subsonic.WEB_002dINF.jsp.login_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_60((net.sourceforge.subsonic.WEB_002dINF.jsp.personalSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_61((net.sourceforge.subsonic.WEB_002dINF.jsp.recover_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_62((net.sourceforge.subsonic.WEB_002dINF.jsp.sonosSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_63((net.sourceforge.subsonic.WEB_002dINF.jsp.homePager_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_64((net.sourceforge.subsonic.WEB_002dINF.jsp.allmusic_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_65((net.sourceforge.subsonic.WEB_002dINF.jsp.index_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_66((net.sourceforge.subsonic.WEB_002dINF.jsp.jquery_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_67((net.sourceforge.subsonic.WEB_002dINF.jsp.playQueue_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_68((net.sourceforge.subsonic.WEB_002dINF.jsp.gettingStarted_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_69((net.sourceforge.subsonic.WEB_002dINF.jsp.advancedSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_70((net.sourceforge.subsonic.WEB_002dINF.jsp.podcast_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_71((net.sourceforge.subsonic.WEB_002dINF.jsp.accessDenied_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_72((net.sourceforge.subsonic.WEB_002dINF.jsp.test_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_73((net.sourceforge.subsonic.WEB_002dINF.jsp.more_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_74((net.sourceforge.subsonic.WEB_002dINF.jsp.reload_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_75((net.sourceforge.subsonic.WEB_002dINF.jsp.artistMain_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_76((net.sourceforge.subsonic.index_jsp) this.servlets[0]);
			} else {
				return new Dispatcher_77((net.sourceforge.subsonic.error_jsp) this.servlets[0]);
			}
		}

		@Override
		public RequestDispatcher getNamedDispatcher(final String url) {
			if(nondetBool()) {
				return new Dispatcher_0((SubsonicModel) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_3((net.sourceforge.subsonic.WEB_002dINF.jsp.playlist_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_4((net.sourceforge.subsonic.WEB_002dINF.jsp.settingsHeader_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_5((net.sourceforge.subsonic.WEB_002dINF.jsp.db_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_6((net.sourceforge.subsonic.WEB_002dINF.jsp.transcodingSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_7((net.sourceforge.subsonic.WEB_002dINF.jsp.podcastReceiver_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_8((net.sourceforge.subsonic.WEB_002dINF.jsp.generalSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_9((net.sourceforge.subsonic.WEB_002dINF.jsp.videoMain_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_10((net.sourceforge.subsonic.WEB_002dINF.jsp.shareSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_11((net.sourceforge.subsonic.WEB_002dINF.jsp.playlists_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_12((net.sourceforge.subsonic.WEB_002dINF.jsp.right_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_13((net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayer_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_14((net.sourceforge.subsonic.WEB_002dINF.jsp.include_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_15((net.sourceforge.subsonic.WEB_002dINF.jsp.internetRadioSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_16((net.sourceforge.subsonic.WEB_002dINF.jsp.createShare_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_17((net.sourceforge.subsonic.WEB_002dINF.jsp.passwordSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_18((net.sourceforge.subsonic.WEB_002dINF.jsp.playQueueCast_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_19((net.sourceforge.subsonic.WEB_002dINF.jsp.status_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_20((net.sourceforge.subsonic.WEB_002dINF.jsp.top_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_21((net.sourceforge.subsonic.WEB_002dINF.jsp.avatarUploadResult_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_22((net.sourceforge.subsonic.WEB_002dINF.jsp.importPlaylist_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_23((net.sourceforge.subsonic.WEB_002dINF.jsp.playerSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_24((net.sourceforge.subsonic.WEB_002dINF.jsp.dlnaSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_25((net.sourceforge.subsonic.WEB_002dINF.jsp.helpToolTip_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_26((net.sourceforge.subsonic.WEB_002dINF.jsp.help_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_27((net.sourceforge.subsonic.WEB_002dINF.jsp.starred_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_28((net.sourceforge.subsonic.WEB_002dINF.jsp.podcastSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_29((net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayerCast_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_30((net.sourceforge.subsonic.WEB_002dINF.jsp.left_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_31((net.sourceforge.subsonic.WEB_002dINF.jsp.playButtons_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_32((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.playlist_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_33((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.settings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_34((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.search_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_35((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.head_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_36((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.loadPlaylist_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_37((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.searchResult_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_38((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.index_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_39((net.sourceforge.subsonic.WEB_002dINF.jsp.wap.browse_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_40((net.sourceforge.subsonic.WEB_002dINF.jsp.rating_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_41((net.sourceforge.subsonic.WEB_002dINF.jsp.albumMain_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_42((net.sourceforge.subsonic.WEB_002dINF.jsp.notFound_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_43((net.sourceforge.subsonic.WEB_002dINF.jsp.upload_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_44((net.sourceforge.subsonic.WEB_002dINF.jsp.premium_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_45((net.sourceforge.subsonic.WEB_002dINF.jsp.userSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_46((net.sourceforge.subsonic.WEB_002dINF.jsp.changeCoverArt_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_47((net.sourceforge.subsonic.WEB_002dINF.jsp.musicFolderSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_48((net.sourceforge.subsonic.WEB_002dINF.jsp.rest.videoPlayer_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_49((net.sourceforge.subsonic.WEB_002dINF.jsp.editTags_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_50((net.sourceforge.subsonic.WEB_002dINF.jsp.licenseNotice_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_51((net.sourceforge.subsonic.WEB_002dINF.jsp.networkSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_52((net.sourceforge.subsonic.WEB_002dINF.jsp.viewSelector_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_53((net.sourceforge.subsonic.WEB_002dINF.jsp.search_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_54((net.sourceforge.subsonic.WEB_002dINF.jsp.home_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_55((net.sourceforge.subsonic.WEB_002dINF.jsp.externalPlayer_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_56((net.sourceforge.subsonic.WEB_002dINF.jsp.head_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_57((net.sourceforge.subsonic.WEB_002dINF.jsp.lyrics_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_58((net.sourceforge.subsonic.WEB_002dINF.jsp.coverArt_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_59((net.sourceforge.subsonic.WEB_002dINF.jsp.login_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_60((net.sourceforge.subsonic.WEB_002dINF.jsp.personalSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_61((net.sourceforge.subsonic.WEB_002dINF.jsp.recover_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_62((net.sourceforge.subsonic.WEB_002dINF.jsp.sonosSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_63((net.sourceforge.subsonic.WEB_002dINF.jsp.homePager_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_64((net.sourceforge.subsonic.WEB_002dINF.jsp.allmusic_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_65((net.sourceforge.subsonic.WEB_002dINF.jsp.index_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_66((net.sourceforge.subsonic.WEB_002dINF.jsp.jquery_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_67((net.sourceforge.subsonic.WEB_002dINF.jsp.playQueue_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_68((net.sourceforge.subsonic.WEB_002dINF.jsp.gettingStarted_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_69((net.sourceforge.subsonic.WEB_002dINF.jsp.advancedSettings_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_70((net.sourceforge.subsonic.WEB_002dINF.jsp.podcast_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_71((net.sourceforge.subsonic.WEB_002dINF.jsp.accessDenied_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_72((net.sourceforge.subsonic.WEB_002dINF.jsp.test_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_73((net.sourceforge.subsonic.WEB_002dINF.jsp.more_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_74((net.sourceforge.subsonic.WEB_002dINF.jsp.reload_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_75((net.sourceforge.subsonic.WEB_002dINF.jsp.artistMain_jsp) this.servlets[0]);
			} else if(nondetBool()) {
				return new Dispatcher_76((net.sourceforge.subsonic.index_jsp) this.servlets[0]);
			} else {
				return new Dispatcher_77((net.sourceforge.subsonic.error_jsp) this.servlets[0]);
			}
		}

		@Override
		public void handlePageException(final Throwable t, final SimpleHttpRequest req, final SimpleHttpResponse resp) throws IOException, ServletException {
			final int disp = nondetInt();
			if(disp == 0) {
				((net.sourceforge.subsonic.error_jsp) this.servlets[0]).service(req, resp);
			}
		}
	}

	public static class SubsonicModel extends HttpServlet {
		private final MainController mainController;
		private final PlaylistController playlistController;
		private final PlaylistsController playlistsController;
		private final HelpController helpController;
		private final LyricsController lyricsController;
		private final LeftController leftController;
		private final RightController rightController;
		private final StatusController statusController;
		private final UploadController uploadController;
		private final MoreController moreController;
		private final ImportPlaylistController importPlaylistController;
		private final MultiController multiController;
		private final SetMusicFileInfoController setMusicFileInfoController;
		private final ShareManagementController shareManagementController;
		private final SetRatingController setRatingController;
		private final TopController topController;
		private final RandomPlayQueueController randomPlayQueueController;
		private final ChangeCoverArtController changeCoverArtController;
		private final VideoPlayerController videoPlayerController;
		private final NowPlayingController nowPlayingController;
		private final StarredController starredController;
		private final SearchController searchController;
		private final SettingsController settingsController;
		private final PlayerSettingsController playerSettingsController;
		private final DLNASettingsController dlnaSettingsController;
		private final SonosSettingsController sonosSettingsController;
		private final ShareSettingsController shareSettingsController;
		private final MusicFolderSettingsController musicFolderSettingsController;
		private final NetworkSettingsController networkSettingsController;
		private final TranscodingSettingsController transcodingSettingsController;
		private final InternetRadioSettingsController internetRadioSettingsController;
		private final PodcastSettingsController podcastSettingsController;
		private final GeneralSettingsController generalSettingsController;
		private final AdvancedSettingsController advancedSettingsController;
		private final PersonalSettingsController personalSettingsController;
		private final AvatarUploadController avatarUploadController;
		private final UserSettingsController userSettingsController;
		private final PasswordSettingsController passwordSettingsController;
		private final AllmusicController allmusicController;
		private final HomeController homeController;
		private final EditTagsController editTagsController;
		private final PlayQueueController playQueueController;
		private final CoverArtController coverArtController;
		private final AvatarController avatarController;
		private final ProxyController proxyController;
		private final StatusChartController statusChartController;
		private final UserChartController userChartController;
		private DownloadController downloadController;
		private final PremiumController premiumController;
		private final DBController dbController;
		private final PodcastReceiverController podcastReceiverController;
		private final PodcastReceiverAdminController podcastReceiverAdminController;
		private final PodcastController podcastController;
		private final WapController wapController;
		private final RESTController restController;
		private final M3UController m3uController;
		private final StreamController streamController;
		private final HLSController hlsController;
		private final ExternalPlayerController externalPlayerController;

		public SubsonicModel(final MainController mainController, final PlaylistController playlistController, final PlaylistsController playlistsController,
				final HelpController helpController, final LyricsController lyricsController, final LeftController leftController, final RightController rightController,
				final StatusController statusController, final MoreController moreController, final UploadController uploadController,
				final ImportPlaylistController importPlaylistController, final MultiController multiController, final SetMusicFileInfoController setMusicFileInfoController,
				final ShareManagementController shareManagementController, final SetRatingController setRatingController, final TopController topController,
				final RandomPlayQueueController randomPlayQueueController, final ChangeCoverArtController changeCoverArtController, final VideoPlayerController videoPlayerController,
				final NowPlayingController nowPlayingController, final StarredController starredController, final SearchController searchController,
				final SettingsController settingsController, final PlayerSettingsController playerSettingsController, final DLNASettingsController dlnaSettingsController,
				final SonosSettingsController sonosSettingsController, final ShareSettingsController shareSettingsController,
				final MusicFolderSettingsController musicFolderSettingsController, final NetworkSettingsController networkSettingsController,
				final TranscodingSettingsController transcodingSettingsController, final InternetRadioSettingsController internetRadioSettingsController,
				final PodcastSettingsController podcastSettingsController, final GeneralSettingsController generalSettingsController,
				final AdvancedSettingsController advancedSettingsController, final PersonalSettingsController personalSettingsController,
				final AvatarUploadController avatarUploadController, final UserSettingsController userSettingsController, final PasswordSettingsController passwordSettingsController,
				final AllmusicController allmusicController, final HomeController homeController, final EditTagsController editTagsController,
				final PlayQueueController playQueueController, final CoverArtController coverArtController, final AvatarController avatarController, final ProxyController proxyController,
				final StatusChartController statusChartController, final UserChartController userChartController, final DownloadController downloadController,
				final PremiumController premiumController, final DBController dbController, final PodcastReceiverController podcastReceiverController,
				final PodcastReceiverAdminController podcastReceiverAdminController, final PodcastController podcastController, final DownloadController downloadController2,
				final WapController wapController, final RESTController restController, final M3UController m3uController, final StreamController streamController,
				final HLSController hlsController, final ExternalPlayerController externalPlayerController) {
			this.mainController = mainController;
			this.playlistController = playlistController;
			this.playlistsController = playlistsController;
			this.helpController = helpController;
			this.lyricsController = lyricsController;
			this.leftController = leftController;
			this.rightController = rightController;
			this.statusController = statusController;
			this.moreController = moreController;
			this.uploadController = uploadController;
			this.importPlaylistController = importPlaylistController;
			this.multiController = multiController;
			this.setMusicFileInfoController = setMusicFileInfoController;
			this.shareManagementController = shareManagementController;
			this.setRatingController = setRatingController;
			this.topController = topController;
			this.randomPlayQueueController = randomPlayQueueController;
			this.changeCoverArtController = changeCoverArtController;
			this.videoPlayerController = videoPlayerController;
			this.nowPlayingController = nowPlayingController;
			this.starredController = starredController;
			this.searchController = searchController;
			this.settingsController = settingsController;
			this.playerSettingsController = playerSettingsController;
			this.dlnaSettingsController = dlnaSettingsController;
			this.sonosSettingsController = sonosSettingsController;
			this.shareSettingsController = shareSettingsController;
			this.musicFolderSettingsController = musicFolderSettingsController;
			this.networkSettingsController = networkSettingsController;
			this.transcodingSettingsController = transcodingSettingsController;
			this.internetRadioSettingsController = internetRadioSettingsController;
			this.podcastSettingsController = podcastSettingsController;
			this.generalSettingsController = generalSettingsController;
			this.advancedSettingsController = advancedSettingsController;
			this.personalSettingsController = personalSettingsController;
			this.avatarUploadController = avatarUploadController;
			this.userSettingsController = userSettingsController;
			this.passwordSettingsController = passwordSettingsController;
			this.allmusicController = allmusicController;
			this.homeController = homeController;
			this.editTagsController = editTagsController;
			this.playQueueController = playQueueController;
			this.coverArtController = coverArtController;
			this.avatarController = avatarController;
			this.proxyController = proxyController;
			this.statusChartController = statusChartController;
			this.userChartController = userChartController;
			this.downloadController = downloadController;
			this.premiumController = premiumController;
			this.dbController = dbController;
			this.podcastReceiverController = podcastReceiverController;
			this.podcastReceiverAdminController = podcastReceiverAdminController;
			this.podcastController = podcastController;
			this.downloadController = downloadController;
			this.wapController = wapController;
			this.restController = restController;
			this.m3uController = m3uController;
			this.streamController = streamController;
			this.hlsController = hlsController;
			this.externalPlayerController = externalPlayerController;
		}

		@Override
		protected void service(final HttpServletRequest arg0, final HttpServletResponse arg1) throws ServletException, IOException {
			if(nondetBool()) {
				try {
					this.hlsController.handleRequest(arg0, arg1);
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.avatarController.handleRequest(arg0, arg1);
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.setRatingController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.userChartController.handleRequest(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/personalSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					this.personalSettingsController.doSubmitAction(this.personalSettingsController.formBackingObject(arg0));
					arg0.getRequestDispatcher("/WEB-INF/jsp/personalSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.lyricsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/lyrics.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.setMusicFileInfoController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.shareManagementController.createShare(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/createShare.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/search.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					Object tmp = this.searchController.formBackingObject(arg0);
					ModelAndView mv = this.searchController.onSubmit(arg0, arg1, tmp, new org.springframework.validation.BindException(tmp, "" + System.currentTimeMillis()));
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/networkSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					this.networkSettingsController.doSubmitAction(this.networkSettingsController.formBackingObject(arg0));
					arg0.getRequestDispatcher("/WEB-INF/jsp/networkSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.sonosSettingsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/sonosSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.m3uController.handleRequest(arg0, arg1);
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.statusChartController.handleRequest(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.topController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/top.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.homeController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/home.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.transcodingSettingsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/transcodingSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.randomPlayQueueController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/reload.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.downloadController.handleRequest(arg0, arg1);
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.dlnaSettingsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/dlnaSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.statusController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/status.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.rightController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/right.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.playlistController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/playlist.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/userSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					this.userSettingsController.doSubmitAction(this.userSettingsController.formBackingObject(arg0));
					arg0.getRequestDispatcher("/WEB-INF/jsp/userSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.proxyController.handleRequest(arg0, arg1);
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/advancedSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					this.advancedSettingsController.doSubmitAction(this.advancedSettingsController.formBackingObject(arg0));
					arg0.getRequestDispatcher("/WEB-INF/jsp/advancedSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.changeCoverArtController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/changeCoverArt.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.avatarUploadController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/avatarUploadResult.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.importPlaylistController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/importPlaylist.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.mainController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.videoPlayerController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/videoPlayer.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/premium.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					Object tmp = this.premiumController.formBackingObject(arg0);
					ModelAndView mv = this.premiumController.onSubmit(arg0, arg1, tmp, new org.springframework.validation.BindException(tmp, "" + System.currentTimeMillis()));
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.externalPlayerController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/externalPlayer.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.podcastController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/podcast.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.playQueueController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/playQueue.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/generalSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					this.generalSettingsController.doSubmitAction(this.generalSettingsController.formBackingObject(arg0));
					arg0.getRequestDispatcher("/WEB-INF/jsp/generalSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.podcastReceiverController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/podcastReceiver.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.nowPlayingController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.ping(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getLicense(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getMusicFolders(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getIndexes(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getGenres(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getSongsByGenre(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getArtists(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getSimilarSongs(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getSimilarSongs2(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getArtistInfo(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getArtistInfo2(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getArtist(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getAlbum(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getSong(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getMusicDirectory(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.search(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.search2(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.search3(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getPlaylists(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getPlaylist(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.jukeboxControl(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.createPlaylist(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.updatePlaylist(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.deletePlaylist(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getAlbumList(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getAlbumList2(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getRandomSongs(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getVideos(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getNowPlaying(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.restController.download(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.restController.stream(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.restController.hls(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.scrobble(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.star(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.unstar(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getStarred(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getStarred2(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getPodcasts(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.refreshPodcasts(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.createPodcastChannel(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.deletePodcastChannel(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.deletePodcastEpisode(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.downloadPodcastEpisode(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getInternetRadioStations(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getBookmarks(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.createBookmark(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.deleteBookmark(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getPlayQueue(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.savePlayQueue(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getShares(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.createShare(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.deleteShare(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.updateShare(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.restController.videoPlayer(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/rest/videoPlayer.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.restController.getCoverArt(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.restController.getAvatar(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.changePassword(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getUser(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getUsers(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.createUser(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.updateUser(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.deleteUser(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getChatMessages(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.addChatMessage(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.getLyrics(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.restController.setRating(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.internetRadioSettingsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/internetRadioSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.multiController.login(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.multiController.recover(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/recover.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.multiController.accessDenied(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/accessDenied.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.multiController.notFound(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/notFound.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.multiController.gettingStarted(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.multiController.index(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/index.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.multiController.exportPlaylist(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.multiController.test(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/test.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.coverArtController.handleRequest(arg0, arg1);
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.playlistsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/playlists.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.shareSettingsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/shareSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.index(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.wap(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/wap/index.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.browse(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/wap/browse.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.playlist(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/wap/playlist.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.loadPlaylist(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/wap/loadPlaylist.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.search(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/wap/search.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.searchResult(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/wap/searchResult.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.settings(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/wap/settings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.wapController.selectPlayer(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.podcastReceiverAdminController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/podcastSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					this.podcastSettingsController.doSubmitAction(this.podcastSettingsController.formBackingObject(arg0));
					arg0.getRequestDispatcher("/WEB-INF/jsp/podcastSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.uploadController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/upload.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/musicFolderSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					Object tmp = this.musicFolderSettingsController.formBackingObject(arg0);
					ModelAndView mv = this.musicFolderSettingsController.onSubmit(tmp);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.dbController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/db.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.leftController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/left.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.settingsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("" + System.currentTimeMillis()).forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.helpController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/help.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/playerSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					this.playerSettingsController.doSubmitAction(this.playerSettingsController.formBackingObject(arg0));
					arg0.getRequestDispatcher("/WEB-INF/jsp/playerSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.allmusicController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/allmusic.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					this.streamController.handleRequest(arg0, arg1);
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				arg0.getRequestDispatcher("/WEB-INF/jsp/passwordSettings.jsp").forward(arg0, arg1);
				return;
			} else if(nondetBool()) {
				try {
					this.passwordSettingsController.doSubmitAction(this.passwordSettingsController.formBackingObject(arg0));
					arg0.getRequestDispatcher("/WEB-INF/jsp/passwordSettings.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.starredController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/starred.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.editTagsController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/editTags.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			} else if(nondetBool()) {
				try {
					ModelAndView mv = this.moreController.handleRequestInternal(arg0, arg1);
					arg0.setAttribute(System.currentTimeMillis() + "", mv.getModel().get(System.currentTimeMillis() + ""));
					arg0.getRequestDispatcher("/WEB-INF/jsp/more.jsp").forward(arg0, arg1);
					return;
				} catch (Exception e) {
					arg0.getRequestDispatcher("/error.jsp").forward(arg0, arg1);
					return;
				}
			}

		}

		@Override
		public void service(final ServletRequest arg0, final ServletResponse arg1) throws ServletException, IOException {
			service((HttpServletRequest) arg0, (HttpServletResponse) arg1);
		}
	}

	public static class Dispatcher_0 implements RequestDispatcher {
		private final SubsonicModel delegate;

		public Dispatcher_0(final SubsonicModel delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_1 implements RequestDispatcher {
		private final org.apache.cxf.transport.servlet.CXFServlet delegate;

		public Dispatcher_1(final org.apache.cxf.transport.servlet.CXFServlet delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_2 implements RequestDispatcher {
		private final org.directwebremoting.servlet.DwrServlet delegate;

		public Dispatcher_2(final org.directwebremoting.servlet.DwrServlet delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_3 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.playlist_jsp delegate;

		public Dispatcher_3(final net.sourceforge.subsonic.WEB_002dINF.jsp.playlist_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_4 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.settingsHeader_jsp delegate;

		public Dispatcher_4(final net.sourceforge.subsonic.WEB_002dINF.jsp.settingsHeader_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_5 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.db_jsp delegate;

		public Dispatcher_5(final net.sourceforge.subsonic.WEB_002dINF.jsp.db_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_6 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.transcodingSettings_jsp delegate;

		public Dispatcher_6(final net.sourceforge.subsonic.WEB_002dINF.jsp.transcodingSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_7 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.podcastReceiver_jsp delegate;

		public Dispatcher_7(final net.sourceforge.subsonic.WEB_002dINF.jsp.podcastReceiver_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_8 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.generalSettings_jsp delegate;

		public Dispatcher_8(final net.sourceforge.subsonic.WEB_002dINF.jsp.generalSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_9 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.videoMain_jsp delegate;

		public Dispatcher_9(final net.sourceforge.subsonic.WEB_002dINF.jsp.videoMain_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_10 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.shareSettings_jsp delegate;

		public Dispatcher_10(final net.sourceforge.subsonic.WEB_002dINF.jsp.shareSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_11 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.playlists_jsp delegate;

		public Dispatcher_11(final net.sourceforge.subsonic.WEB_002dINF.jsp.playlists_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_12 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.right_jsp delegate;

		public Dispatcher_12(final net.sourceforge.subsonic.WEB_002dINF.jsp.right_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_13 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayer_jsp delegate;

		public Dispatcher_13(final net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayer_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_14 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.include_jsp delegate;

		public Dispatcher_14(final net.sourceforge.subsonic.WEB_002dINF.jsp.include_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_15 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.internetRadioSettings_jsp delegate;

		public Dispatcher_15(final net.sourceforge.subsonic.WEB_002dINF.jsp.internetRadioSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_16 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.createShare_jsp delegate;

		public Dispatcher_16(final net.sourceforge.subsonic.WEB_002dINF.jsp.createShare_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_17 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.passwordSettings_jsp delegate;

		public Dispatcher_17(final net.sourceforge.subsonic.WEB_002dINF.jsp.passwordSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_18 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.playQueueCast_jsp delegate;

		public Dispatcher_18(final net.sourceforge.subsonic.WEB_002dINF.jsp.playQueueCast_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_19 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.status_jsp delegate;

		public Dispatcher_19(final net.sourceforge.subsonic.WEB_002dINF.jsp.status_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_20 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.top_jsp delegate;

		public Dispatcher_20(final net.sourceforge.subsonic.WEB_002dINF.jsp.top_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_21 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.avatarUploadResult_jsp delegate;

		public Dispatcher_21(final net.sourceforge.subsonic.WEB_002dINF.jsp.avatarUploadResult_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_22 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.importPlaylist_jsp delegate;

		public Dispatcher_22(final net.sourceforge.subsonic.WEB_002dINF.jsp.importPlaylist_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_23 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.playerSettings_jsp delegate;

		public Dispatcher_23(final net.sourceforge.subsonic.WEB_002dINF.jsp.playerSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_24 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.dlnaSettings_jsp delegate;

		public Dispatcher_24(final net.sourceforge.subsonic.WEB_002dINF.jsp.dlnaSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_25 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.helpToolTip_jsp delegate;

		public Dispatcher_25(final net.sourceforge.subsonic.WEB_002dINF.jsp.helpToolTip_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_26 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.help_jsp delegate;

		public Dispatcher_26(final net.sourceforge.subsonic.WEB_002dINF.jsp.help_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_27 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.starred_jsp delegate;

		public Dispatcher_27(final net.sourceforge.subsonic.WEB_002dINF.jsp.starred_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_28 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.podcastSettings_jsp delegate;

		public Dispatcher_28(final net.sourceforge.subsonic.WEB_002dINF.jsp.podcastSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_29 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayerCast_jsp delegate;

		public Dispatcher_29(final net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayerCast_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_30 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.left_jsp delegate;

		public Dispatcher_30(final net.sourceforge.subsonic.WEB_002dINF.jsp.left_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_31 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.playButtons_jsp delegate;

		public Dispatcher_31(final net.sourceforge.subsonic.WEB_002dINF.jsp.playButtons_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_32 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.playlist_jsp delegate;

		public Dispatcher_32(final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.playlist_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_33 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.settings_jsp delegate;

		public Dispatcher_33(final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.settings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_34 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.search_jsp delegate;

		public Dispatcher_34(final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.search_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_35 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.head_jsp delegate;

		public Dispatcher_35(final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.head_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_36 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.loadPlaylist_jsp delegate;

		public Dispatcher_36(final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.loadPlaylist_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_37 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.searchResult_jsp delegate;

		public Dispatcher_37(final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.searchResult_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_38 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.index_jsp delegate;

		public Dispatcher_38(final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.index_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_39 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.browse_jsp delegate;

		public Dispatcher_39(final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.browse_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_40 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.rating_jsp delegate;

		public Dispatcher_40(final net.sourceforge.subsonic.WEB_002dINF.jsp.rating_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_41 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.albumMain_jsp delegate;

		public Dispatcher_41(final net.sourceforge.subsonic.WEB_002dINF.jsp.albumMain_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_42 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.notFound_jsp delegate;

		public Dispatcher_42(final net.sourceforge.subsonic.WEB_002dINF.jsp.notFound_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_43 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.upload_jsp delegate;

		public Dispatcher_43(final net.sourceforge.subsonic.WEB_002dINF.jsp.upload_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_44 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.premium_jsp delegate;

		public Dispatcher_44(final net.sourceforge.subsonic.WEB_002dINF.jsp.premium_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_45 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.userSettings_jsp delegate;

		public Dispatcher_45(final net.sourceforge.subsonic.WEB_002dINF.jsp.userSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_46 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.changeCoverArt_jsp delegate;

		public Dispatcher_46(final net.sourceforge.subsonic.WEB_002dINF.jsp.changeCoverArt_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_47 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.musicFolderSettings_jsp delegate;

		public Dispatcher_47(final net.sourceforge.subsonic.WEB_002dINF.jsp.musicFolderSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_48 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.rest.videoPlayer_jsp delegate;

		public Dispatcher_48(final net.sourceforge.subsonic.WEB_002dINF.jsp.rest.videoPlayer_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_49 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.editTags_jsp delegate;

		public Dispatcher_49(final net.sourceforge.subsonic.WEB_002dINF.jsp.editTags_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_50 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.licenseNotice_jsp delegate;

		public Dispatcher_50(final net.sourceforge.subsonic.WEB_002dINF.jsp.licenseNotice_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_51 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.networkSettings_jsp delegate;

		public Dispatcher_51(final net.sourceforge.subsonic.WEB_002dINF.jsp.networkSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_52 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.viewSelector_jsp delegate;

		public Dispatcher_52(final net.sourceforge.subsonic.WEB_002dINF.jsp.viewSelector_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_53 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.search_jsp delegate;

		public Dispatcher_53(final net.sourceforge.subsonic.WEB_002dINF.jsp.search_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_54 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.home_jsp delegate;

		public Dispatcher_54(final net.sourceforge.subsonic.WEB_002dINF.jsp.home_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_55 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.externalPlayer_jsp delegate;

		public Dispatcher_55(final net.sourceforge.subsonic.WEB_002dINF.jsp.externalPlayer_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_56 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.head_jsp delegate;

		public Dispatcher_56(final net.sourceforge.subsonic.WEB_002dINF.jsp.head_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_57 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.lyrics_jsp delegate;

		public Dispatcher_57(final net.sourceforge.subsonic.WEB_002dINF.jsp.lyrics_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_58 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.coverArt_jsp delegate;

		public Dispatcher_58(final net.sourceforge.subsonic.WEB_002dINF.jsp.coverArt_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_59 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.login_jsp delegate;

		public Dispatcher_59(final net.sourceforge.subsonic.WEB_002dINF.jsp.login_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_60 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.personalSettings_jsp delegate;

		public Dispatcher_60(final net.sourceforge.subsonic.WEB_002dINF.jsp.personalSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_61 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.recover_jsp delegate;

		public Dispatcher_61(final net.sourceforge.subsonic.WEB_002dINF.jsp.recover_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_62 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.sonosSettings_jsp delegate;

		public Dispatcher_62(final net.sourceforge.subsonic.WEB_002dINF.jsp.sonosSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_63 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.homePager_jsp delegate;

		public Dispatcher_63(final net.sourceforge.subsonic.WEB_002dINF.jsp.homePager_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_64 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.allmusic_jsp delegate;

		public Dispatcher_64(final net.sourceforge.subsonic.WEB_002dINF.jsp.allmusic_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_65 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.index_jsp delegate;

		public Dispatcher_65(final net.sourceforge.subsonic.WEB_002dINF.jsp.index_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_66 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.jquery_jsp delegate;

		public Dispatcher_66(final net.sourceforge.subsonic.WEB_002dINF.jsp.jquery_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_67 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.playQueue_jsp delegate;

		public Dispatcher_67(final net.sourceforge.subsonic.WEB_002dINF.jsp.playQueue_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_68 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.gettingStarted_jsp delegate;

		public Dispatcher_68(final net.sourceforge.subsonic.WEB_002dINF.jsp.gettingStarted_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_69 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.advancedSettings_jsp delegate;

		public Dispatcher_69(final net.sourceforge.subsonic.WEB_002dINF.jsp.advancedSettings_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_70 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.podcast_jsp delegate;

		public Dispatcher_70(final net.sourceforge.subsonic.WEB_002dINF.jsp.podcast_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_71 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.accessDenied_jsp delegate;

		public Dispatcher_71(final net.sourceforge.subsonic.WEB_002dINF.jsp.accessDenied_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_72 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.test_jsp delegate;

		public Dispatcher_72(final net.sourceforge.subsonic.WEB_002dINF.jsp.test_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_73 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.more_jsp delegate;

		public Dispatcher_73(final net.sourceforge.subsonic.WEB_002dINF.jsp.more_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_74 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.reload_jsp delegate;

		public Dispatcher_74(final net.sourceforge.subsonic.WEB_002dINF.jsp.reload_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_75 implements RequestDispatcher {
		private final net.sourceforge.subsonic.WEB_002dINF.jsp.artistMain_jsp delegate;

		public Dispatcher_75(final net.sourceforge.subsonic.WEB_002dINF.jsp.artistMain_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_76 implements RequestDispatcher {
		private final net.sourceforge.subsonic.index_jsp delegate;

		public Dispatcher_76(final net.sourceforge.subsonic.index_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class Dispatcher_77 implements RequestDispatcher {
		private final net.sourceforge.subsonic.error_jsp delegate;

		public Dispatcher_77(final net.sourceforge.subsonic.error_jsp delegate) {
			this.delegate = delegate;
		}

		@Override
		public void forward(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}

		@Override
		public void include(final ServletRequest req, final ServletResponse resp) throws IOException, ServletException {
			this.delegate.service(req, resp);
		}
	}

	public static class DummyAcegiFilter implements Filter {
		private final SubsonicLdapBindAuthenticator bind;
		private final SecurityService ss;

		public DummyAcegiFilter(final SecurityService ss, final SubsonicLdapBindAuthenticator bind) {
			this.ss = ss;
			this.bind = bind;
		}

		@Override
		public void destroy() {
		}

		@Override
		public void doFilter(final ServletRequest arg0, final ServletResponse arg1, final FilterChain arg2) throws IOException, ServletException {
			if(nondetBool()) {
				bind.authenticate("foo", "bar");
			}
			this.ss.loadUserByUsername("foo");
			if(nondetBool()) {
				arg2.doFilter(arg0, arg1);
			}
		}

		@Override
		public void init(final FilterConfig arg0) throws ServletException {
		}

	}

	public static void main(final String[] args) throws Exception {
		final net.sourceforge.subsonic.i18n.SubsonicLocaleResolver localeResolver = new net.sourceforge.subsonic.i18n.SubsonicLocaleResolver();
		final PersonalSettingsController personalSettingsController = new net.sourceforge.subsonic.controller.PersonalSettingsController();
		final net.sourceforge.subsonic.controller.ShareManagementController shareManagementController = new net.sourceforge.subsonic.controller.ShareManagementController();
		final net.sourceforge.subsonic.ajax.MultiService ajaxMultiService = new net.sourceforge.subsonic.ajax.MultiService();
		final net.sourceforge.subsonic.service.PodcastService podcastService = new net.sourceforge.subsonic.service.PodcastService();
		final net.sourceforge.subsonic.dao.UserDao userDao = new net.sourceforge.subsonic.dao.UserDao();
		final net.sourceforge.subsonic.dao.PlayQueueDao playQueueDao = new net.sourceforge.subsonic.dao.PlayQueueDao();
		final org.acegisecurity.intercept.method.aopalliance.MethodSecurityInterceptor ajaxServiceInterceptor = new org.acegisecurity.intercept.method.aopalliance.MethodSecurityInterceptor();
		final net.sourceforge.subsonic.validator.PremiumValidator premiumValidator = new net.sourceforge.subsonic.validator.PremiumValidator();
		final net.sourceforge.subsonic.service.SearchService searchService = new net.sourceforge.subsonic.service.SearchService();
		final net.sourceforge.subsonic.controller.MoreController moreController = new net.sourceforge.subsonic.controller.MoreController();
		final net.sourceforge.subsonic.ajax.PlaylistService ajaxPlaylistService = new net.sourceforge.subsonic.ajax.PlaylistService();
		final org.acegisecurity.ui.basicauth.BasicProcessingFilter basicProcessingFilter = new org.acegisecurity.ui.basicauth.BasicProcessingFilter();
		final net.sourceforge.subsonic.controller.InternetRadioSettingsController internetRadioSettingsController = new net.sourceforge.subsonic.controller.InternetRadioSettingsController();
		final net.sourceforge.subsonic.controller.PasswordSettingsController passwordSettingsController = new net.sourceforge.subsonic.controller.PasswordSettingsController();
		final org.acegisecurity.vote.AffirmativeBased accessDecisionManager = new org.acegisecurity.vote.AffirmativeBased();
		final net.sourceforge.subsonic.service.VersionService versionService = new net.sourceforge.subsonic.service.VersionService();
		final net.sourceforge.subsonic.controller.ImportPlaylistController importPlaylistController = new net.sourceforge.subsonic.controller.ImportPlaylistController();
		final net.sourceforge.subsonic.dao.AvatarDao avatarDao = new net.sourceforge.subsonic.dao.AvatarDao();
		final net.sourceforge.subsonic.dao.AlbumDao albumDao = new net.sourceforge.subsonic.dao.AlbumDao();
		final net.sourceforge.subsonic.dao.MediaFileDao mediaFileDao = new net.sourceforge.subsonic.dao.MediaFileDao();
		final net.sourceforge.subsonic.controller.M3UController m3uController = new net.sourceforge.subsonic.controller.M3UController();
		final org.springframework.web.servlet.view.InternalResourceViewResolver viewResolver = new org.springframework.web.servlet.view.InternalResourceViewResolver();
		final org.acegisecurity.ui.webapp.AuthenticationProcessingFilter authenticationProcessingFilter = new org.acegisecurity.ui.webapp.AuthenticationProcessingFilter();
		final net.sourceforge.subsonic.controller.AllmusicController allmusicController = new net.sourceforge.subsonic.controller.AllmusicController();
		final net.sourceforge.subsonic.controller.DownloadController downloadController = new net.sourceforge.subsonic.controller.DownloadController();
		final net.sourceforge.subsonic.controller.RESTController restController = new net.sourceforge.subsonic.controller.RESTController();
		final net.sourceforge.subsonic.controller.WapController wapController = new net.sourceforge.subsonic.controller.WapController();
		final net.sourceforge.subsonic.controller.SetMusicFileInfoController setMusicFileInfoController = new net.sourceforge.subsonic.controller.SetMusicFileInfoController();
		final net.sourceforge.subsonic.service.PlaylistService playlistService = new net.sourceforge.subsonic.service.PlaylistService();
		final net.sourceforge.subsonic.ajax.LyricsService ajaxLyricsService = new net.sourceforge.subsonic.ajax.LyricsService();
		final org.acegisecurity.ui.logout.SecurityContextLogoutHandler inline_bean_0 = new org.acegisecurity.ui.logout.SecurityContextLogoutHandler();
		final org.acegisecurity.ui.webapp.AuthenticationProcessingFilterEntryPoint inline_bean_1 = new org.acegisecurity.ui.webapp.AuthenticationProcessingFilterEntryPoint();
		final org.acegisecurity.ui.AccessDeniedHandlerImpl inline_bean_2 = new org.acegisecurity.ui.AccessDeniedHandlerImpl();
		final org.acegisecurity.ui.basicauth.BasicProcessingFilterEntryPoint inline_bean_3 = new org.acegisecurity.ui.basicauth.BasicProcessingFilterEntryPoint();
		final org.acegisecurity.vote.RoleVoter inline_bean_4 = new org.acegisecurity.vote.RoleVoter();
		final org.acegisecurity.vote.AuthenticatedVoter inline_bean_5 = new org.acegisecurity.vote.AuthenticatedVoter();
		final org.acegisecurity.providers.anonymous.AnonymousAuthenticationProvider inline_bean_6 = new org.acegisecurity.providers.anonymous.AnonymousAuthenticationProvider();
		final org.acegisecurity.providers.rememberme.RememberMeAuthenticationProvider inline_bean_7 = new org.acegisecurity.providers.rememberme.RememberMeAuthenticationProvider();
		final net.sourceforge.subsonic.service.metadata.JaudiotaggerParser inline_bean_8 = new net.sourceforge.subsonic.service.metadata.JaudiotaggerParser();
		final net.sourceforge.subsonic.service.metadata.FFmpegParser inline_bean_9 = new net.sourceforge.subsonic.service.metadata.FFmpegParser();
		final org.acegisecurity.providers.dao.DaoAuthenticationProvider daoAuthenticationProvider = new org.acegisecurity.providers.dao.DaoAuthenticationProvider();
		final net.sourceforge.subsonic.controller.PodcastReceiverController podcastReceiverController = new net.sourceforge.subsonic.controller.PodcastReceiverController();
		final net.sourceforge.subsonic.controller.TopController topController = new net.sourceforge.subsonic.controller.TopController();
		final org.acegisecurity.util.FilterChainProxy filterChainProxy = new org.acegisecurity.util.FilterChainProxy();
		final net.sourceforge.subsonic.dao.PodcastDao podcastDao = new net.sourceforge.subsonic.dao.PodcastDao();
		final net.sourceforge.subsonic.cache.CacheFactory cacheFactory = new net.sourceforge.subsonic.cache.CacheFactory();
		final net.sourceforge.subsonic.dao.ArtistDao artistDao = new net.sourceforge.subsonic.dao.ArtistDao();
		final net.sourceforge.subsonic.controller.ReloadFrame inline_bean_11 = new net.sourceforge.subsonic.controller.ReloadFrame();
		final net.sourceforge.subsonic.ldap.UserDetailsServiceBasedAuthoritiesPopulator userDetailsServiceBasedAuthoritiesPopulator = new net.sourceforge.subsonic.ldap.UserDetailsServiceBasedAuthoritiesPopulator();
		final net.sourceforge.subsonic.service.RatingService ratingService = new net.sourceforge.subsonic.service.RatingService();
		final net.sourceforge.subsonic.controller.StreamController streamController = new net.sourceforge.subsonic.controller.StreamController();
		final org.acegisecurity.providers.anonymous.AnonymousProcessingFilter anonymousProcessingFilter = new org.acegisecurity.providers.anonymous.AnonymousProcessingFilter();
		final net.sourceforge.subsonic.service.SonosService sonosService = new net.sourceforge.subsonic.service.SonosService();
		final net.sourceforge.subsonic.controller.PlaylistsController playlistsController = new net.sourceforge.subsonic.controller.PlaylistsController();
		final net.sourceforge.subsonic.controller.AvatarController avatarController = new net.sourceforge.subsonic.controller.AvatarController();
		final net.sourceforge.subsonic.service.PlayerService playerService = new net.sourceforge.subsonic.service.PlayerService();
		final net.sourceforge.subsonic.service.TranscodingService transcodingService = new net.sourceforge.subsonic.service.TranscodingService();
		final net.sourceforge.subsonic.controller.ShareSettingsController shareSettingsController = new net.sourceforge.subsonic.controller.ShareSettingsController();
		final net.sourceforge.subsonic.controller.DBController dbController = new net.sourceforge.subsonic.controller.DBController();
		final org.springframework.web.servlet.handler.SimpleUrlHandlerMapping urlMapping = new org.springframework.web.servlet.handler.SimpleUrlHandlerMapping();
		final net.sourceforge.subsonic.service.JukeboxService jukeboxService = new net.sourceforge.subsonic.service.JukeboxService();
		final net.sourceforge.subsonic.controller.MainController mainController = new net.sourceforge.subsonic.controller.MainController();
		final net.sourceforge.subsonic.service.UPnPService upnpService = new net.sourceforge.subsonic.service.UPnPService();
		final net.sourceforge.subsonic.controller.SonosSettingsController sonosSettingsController = new net.sourceforge.subsonic.controller.SonosSettingsController();
		final org.springframework.context.support.ResourceBundleMessageSource messageSource = new org.springframework.context.support.ResourceBundleMessageSource();
		final net.sourceforge.subsonic.controller.SearchController searchController = new net.sourceforge.subsonic.controller.SearchController();
		final net.sourceforge.subsonic.validator.UserSettingsValidator userSettingsValidator = new net.sourceforge.subsonic.validator.UserSettingsValidator();
		final net.sourceforge.subsonic.controller.HelpController helpController = new net.sourceforge.subsonic.controller.HelpController();
		final net.sourceforge.subsonic.theme.SubsonicThemeResolver themeResolver = new net.sourceforge.subsonic.theme.SubsonicThemeResolver();
		final net.sourceforge.subsonic.controller.PodcastController podcastController = new net.sourceforge.subsonic.controller.PodcastController();
		final net.sourceforge.subsonic.service.StatusService statusService = new net.sourceforge.subsonic.service.StatusService();
		final net.sourceforge.subsonic.service.MediaScannerService mediaScannerService = new net.sourceforge.subsonic.service.MediaScannerService();
		final net.sourceforge.subsonic.controller.GeneralSettingsController generalSettingsController = new net.sourceforge.subsonic.controller.GeneralSettingsController();
		final net.sourceforge.subsonic.service.MediaFileService mediaFileService = new net.sourceforge.subsonic.service.MediaFileService();
		final net.sourceforge.subsonic.controller.PodcastSettingsController podcastSettingsController = new net.sourceforge.subsonic.controller.PodcastSettingsController();
		final net.sourceforge.subsonic.controller.VideoPlayerController videoPlayerController = new net.sourceforge.subsonic.controller.VideoPlayerController();
		final net.sourceforge.subsonic.ldap.SubsonicLdapBindAuthenticator bindAuthenticator = new net.sourceforge.subsonic.ldap.SubsonicLdapBindAuthenticator();
		final org.acegisecurity.providers.ldap.LdapAuthenticationProvider ldapAuthenticationProvider = new org.acegisecurity.providers.ldap.LdapAuthenticationProvider(
				bindAuthenticator, userDetailsServiceBasedAuthoritiesPopulator);
		final net.sourceforge.subsonic.dao.BookmarkDao bookmarkDao = new net.sourceforge.subsonic.dao.BookmarkDao();
		final net.sourceforge.subsonic.controller.NowPlayingController nowPlayingController = new net.sourceforge.subsonic.controller.NowPlayingController();
		final net.sourceforge.subsonic.dao.DaoHelper daoHelper = new net.sourceforge.subsonic.dao.DaoHelper();
		final org.acegisecurity.ui.basicauth.BasicProcessingFilterEntryPoint basicProcessingFilterEntryPoint = new org.acegisecurity.ui.basicauth.BasicProcessingFilterEntryPoint();
		final org.acegisecurity.providers.dao.cache.EhCacheBasedUserCache userCacheWrapper = new org.acegisecurity.providers.dao.cache.EhCacheBasedUserCache();
		final org.acegisecurity.providers.ProviderManager authenticationManager = new org.acegisecurity.providers.ProviderManager();
		final net.sourceforge.subsonic.controller.PremiumController premiumController = new net.sourceforge.subsonic.controller.PremiumController();
		final net.sourceforge.subsonic.controller.PlaylistController playlistController = new net.sourceforge.subsonic.controller.PlaylistController();
		final net.sourceforge.subsonic.dao.TranscodingDao transcodingDao = new net.sourceforge.subsonic.dao.TranscodingDao();
		final net.sourceforge.subsonic.dao.MusicFolderDao musicFolderDao = new net.sourceforge.subsonic.dao.MusicFolderDao();
		final net.sourceforge.subsonic.controller.UserSettingsController userSettingsController = new net.sourceforge.subsonic.controller.UserSettingsController();
		final net.sourceforge.subsonic.service.sonos.SonosHelper sonosHelper = new net.sourceforge.subsonic.service.sonos.SonosHelper();
		final net.sourceforge.subsonic.validator.PasswordSettingsValidator passwordSettingsValidator = new net.sourceforge.subsonic.validator.PasswordSettingsValidator();
		final net.sourceforge.subsonic.ajax.NowPlayingService ajaxNowPlayingService = new net.sourceforge.subsonic.ajax.NowPlayingService();
		final net.sourceforge.subsonic.controller.EditTagsController editTagsController = new net.sourceforge.subsonic.controller.EditTagsController();
		final net.sourceforge.subsonic.controller.LyricsController lyricsController = new net.sourceforge.subsonic.controller.LyricsController();
		final net.sourceforge.subsonic.controller.MultiController multiController = new net.sourceforge.subsonic.controller.MultiController();
		final net.sourceforge.subsonic.ajax.PlayQueueService ajaxPlayQueueService = new net.sourceforge.subsonic.ajax.PlayQueueService();
		final net.sourceforge.subsonic.controller.PlayQueueController playQueueController = new net.sourceforge.subsonic.controller.PlayQueueController();
		final org.acegisecurity.wrapper.SecurityContextHolderAwareRequestFilter securityContextHolderAwareRequestFilter = new org.acegisecurity.wrapper.SecurityContextHolderAwareRequestFilter();
		final net.sourceforge.subsonic.controller.AvatarUploadController avatarUploadController = new net.sourceforge.subsonic.controller.AvatarUploadController();
		final net.sourceforge.subsonic.dao.PlayerDao playerDao = new net.sourceforge.subsonic.dao.PlayerDao();
		final net.sourceforge.subsonic.service.AudioScrobblerService audioScrobblerService = new net.sourceforge.subsonic.service.AudioScrobblerService();
		final net.sourceforge.subsonic.ajax.StarService ajaxStarService = new net.sourceforge.subsonic.ajax.StarService();
		final net.sourceforge.subsonic.service.ShareService shareService = new net.sourceforge.subsonic.service.ShareService();
		final net.sourceforge.subsonic.dao.PlaylistDao playlistDao = new net.sourceforge.subsonic.dao.PlaylistDao();
		final net.sourceforge.subsonic.controller.CoverArtController coverArtController = new net.sourceforge.subsonic.controller.CoverArtController();
		final net.sourceforge.subsonic.service.upnp.FolderBasedContentDirectory folderBasedContentDirectory = new net.sourceforge.subsonic.service.upnp.FolderBasedContentDirectory();
		final net.sourceforge.subsonic.controller.HomeController homeController = new net.sourceforge.subsonic.controller.HomeController();
		final org.acegisecurity.intercept.web.FilterSecurityInterceptor filterInvocationInterceptor = new org.acegisecurity.intercept.web.FilterSecurityInterceptor();
		final org.acegisecurity.context.HttpSessionContextIntegrationFilter httpSessionContextIntegrationFilter = new org.acegisecurity.context.HttpSessionContextIntegrationFilter();
		final net.sourceforge.subsonic.theme.SubsonicThemeSource themeSource = new net.sourceforge.subsonic.theme.SubsonicThemeSource();
		final net.sourceforge.subsonic.ajax.TagService ajaxTagService = new net.sourceforge.subsonic.ajax.TagService();
		final net.sourceforge.subsonic.controller.UploadController uploadController = new net.sourceforge.subsonic.controller.UploadController();
		final net.sourceforge.subsonic.controller.StarredController starredController = new net.sourceforge.subsonic.controller.StarredController();
		final net.sourceforge.subsonic.service.metadata.MetaDataParserFactory metaDataParserFactory = new net.sourceforge.subsonic.service.metadata.MetaDataParserFactory();
		final net.sourceforge.subsonic.dao.ShareDao shareDao = new net.sourceforge.subsonic.dao.ShareDao();
		final net.sourceforge.subsonic.controller.HLSController hlsController = new net.sourceforge.subsonic.controller.HLSController();
		final net.sourceforge.subsonic.service.LastFmService lastFmService = new net.sourceforge.subsonic.service.LastFmService();
		final org.acegisecurity.ui.ExceptionTranslationFilter exceptionTranslationFilter = new org.acegisecurity.ui.ExceptionTranslationFilter();
		final org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices rememberMeServices = new org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices();
		final net.sourceforge.subsonic.controller.DLNASettingsController dlnaSettingsController = new net.sourceforge.subsonic.controller.DLNASettingsController();
		final net.sourceforge.subsonic.controller.MusicFolderSettingsController musicFolderSettingsController = new net.sourceforge.subsonic.controller.MusicFolderSettingsController();
		final net.sourceforge.subsonic.controller.ExternalPlayerController externalPlayerController = new net.sourceforge.subsonic.controller.ExternalPlayerController();
		final org.acegisecurity.ui.rememberme.RememberMeProcessingFilter rememberMeProcessingFilter = new org.acegisecurity.ui.rememberme.RememberMeProcessingFilter();
		final net.sourceforge.subsonic.service.SecurityService securityService = new net.sourceforge.subsonic.service.SecurityService();
		final net.sourceforge.subsonic.dao.RatingDao musicFileInfoDao = new net.sourceforge.subsonic.dao.RatingDao();
		final org.springframework.aop.framework.ProxyFactoryBean ajaxTransferServiceSecure = new org.springframework.aop.framework.ProxyFactoryBean();
		final net.sourceforge.subsonic.controller.PodcastReceiverAdminController podcastReceiverAdminController = new net.sourceforge.subsonic.controller.PodcastReceiverAdminController();
		final net.sourceforge.subsonic.controller.StatusChartController statusChartController = new net.sourceforge.subsonic.controller.StatusChartController();
		final net.sourceforge.subsonic.controller.PlayerSettingsController playerSettingsController = new net.sourceforge.subsonic.controller.PlayerSettingsController();
		final net.sourceforge.subsonic.service.MusicIndexService musicIndexService = new net.sourceforge.subsonic.service.MusicIndexService();
		final net.sourceforge.subsonic.ajax.ChatService ajaxChatService = new net.sourceforge.subsonic.ajax.ChatService();
		final net.sourceforge.subsonic.service.metadata.DefaultMetaDataParser inline_bean_10 = new net.sourceforge.subsonic.service.metadata.DefaultMetaDataParser();
		final net.sourceforge.subsonic.controller.UserChartController userChartController = new net.sourceforge.subsonic.controller.UserChartController();
		final net.sourceforge.subsonic.controller.RightController rightController = new net.sourceforge.subsonic.controller.RightController();
		final net.sourceforge.subsonic.controller.SetRatingController setRatingController = new net.sourceforge.subsonic.controller.SetRatingController();
		final net.sourceforge.subsonic.controller.NetworkSettingsController networkSettingsController = new net.sourceforge.subsonic.controller.NetworkSettingsController();
		final net.sourceforge.subsonic.controller.AdvancedSettingsController advancedSettingsController = new net.sourceforge.subsonic.controller.AdvancedSettingsController();
		final net.sourceforge.subsonic.security.RESTRequestParameterProcessingFilter restRequestParameterProcessingFilter = new net.sourceforge.subsonic.security.RESTRequestParameterProcessingFilter();
		final net.sourceforge.subsonic.controller.RandomPlayQueueController randomPlayQueueController = new net.sourceforge.subsonic.controller.RandomPlayQueueController();
		final org.springframework.aop.framework.ProxyFactoryBean ajaxTagServiceSecure = new org.springframework.aop.framework.ProxyFactoryBean();
		final net.sourceforge.subsonic.controller.SettingsController settingsController = new net.sourceforge.subsonic.controller.SettingsController();
		final net.sourceforge.subsonic.dao.InternetRadioDao internetRadioDao = new net.sourceforge.subsonic.dao.InternetRadioDao();
		final net.sourceforge.subsonic.controller.TranscodingSettingsController transcodingSettingsController = new net.sourceforge.subsonic.controller.TranscodingSettingsController();
		final net.sourceforge.subsonic.controller.ChangeCoverArtController changeCoverArtController = new net.sourceforge.subsonic.controller.ChangeCoverArtController();
		final net.sourceforge.subsonic.service.SettingsService settingsService = new net.sourceforge.subsonic.service.SettingsService();
		final net.sourceforge.subsonic.service.NetworkService networkService = new net.sourceforge.subsonic.service.NetworkService();
		final net.sourceforge.subsonic.controller.ReloadFrame inline_bean_12 = new net.sourceforge.subsonic.controller.ReloadFrame();
		final net.sourceforge.subsonic.ajax.CoverArtService ajaxCoverArtService = new net.sourceforge.subsonic.ajax.CoverArtService();
		final net.sourceforge.subsonic.controller.ProxyController proxyController = new net.sourceforge.subsonic.controller.ProxyController();
		final net.sourceforge.subsonic.controller.LeftController leftController = new net.sourceforge.subsonic.controller.LeftController();
		final org.acegisecurity.ui.logout.LogoutFilter logoutFilter = new org.acegisecurity.ui.logout.LogoutFilter("/login.view?logout", new LogoutHandler[] { rememberMeServices,
				inline_bean_0 });
		final net.sourceforge.subsonic.service.AdService adService = new net.sourceforge.subsonic.service.AdService();
		final net.sourceforge.subsonic.ajax.TransferService ajaxTransferService = new net.sourceforge.subsonic.ajax.TransferService();
		final net.sourceforge.subsonic.controller.StatusController statusController = new net.sourceforge.subsonic.controller.StatusController();
		final org.acegisecurity.ui.ExceptionTranslationFilter basicExceptionTranslationFilter = new org.acegisecurity.ui.ExceptionTranslationFilter();
		final Ehcache userCache = cacheFactory.getCache("userCache");

		localeResolver.setSecurityService(securityService);
		localeResolver.setSettingsService(settingsService);
		personalSettingsController.setSecurityService(securityService);
		personalSettingsController.setSuccessView("personalSettings");
		personalSettingsController.setSettingsService(settingsService);
		personalSettingsController.setCommandClass(net.sourceforge.subsonic.command.PersonalSettingsCommand.class);
		personalSettingsController.setFormView("personalSettings");
		shareManagementController.setPlayerService(playerService);
		shareManagementController.setShareService(shareService);
		shareManagementController.setSettingsService(settingsService);
		shareManagementController.setSecurityService(securityService);
		shareManagementController.setMediaFileService(mediaFileService);
		shareManagementController.setPlaylistService(playlistService);
		ajaxMultiService.setSecurityService(securityService);
		ajaxMultiService.setNetworkService(networkService);
		ajaxMultiService.setMediaFileService(mediaFileService);
		ajaxMultiService.setSettingsService(settingsService);
		ajaxMultiService.setLastFmService(lastFmService);
		podcastService.setSecurityService(securityService);
		podcastService.setMetaDataParserFactory(metaDataParserFactory);
		podcastService.setSettingsService(settingsService);
		podcastService.setPodcastDao(podcastDao);
		podcastService.setMediaFileService(mediaFileService);
		userDao.setDaoHelper(daoHelper);
		playQueueDao.setDaoHelper(daoHelper);
		ajaxServiceInterceptor.setAccessDecisionManager(accessDecisionManager);
		ajaxServiceInterceptor.setObjectDefinitionSource(null);
		ajaxServiceInterceptor.setAuthenticationManager(authenticationManager);
		premiumValidator.setSettingsService(settingsService);
		searchService.setArtistDao(artistDao);
		searchService.setMediaFileService(mediaFileService);
		searchService.setAlbumDao(albumDao);
		moreController.setSecurityService(securityService);
		moreController.setPlayerService(playerService);
		moreController.setSettingsService(settingsService);
		moreController.setViewName("more");
		moreController.setMediaFileService(mediaFileService);
		ajaxPlaylistService.setPlayerService(playerService);
		ajaxPlaylistService.setLocaleResolver(localeResolver);
		ajaxPlaylistService.setMediaFileDao(mediaFileDao);
		ajaxPlaylistService.setSettingsService(settingsService);
		ajaxPlaylistService.setSecurityService(securityService);
		ajaxPlaylistService.setMediaFileService(mediaFileService);
		ajaxPlaylistService.setPlaylistService(playlistService);
		basicProcessingFilter.setAuthenticationEntryPoint(basicProcessingFilterEntryPoint);
		basicProcessingFilter.setAuthenticationManager(authenticationManager);
		internetRadioSettingsController.setSettingsService(settingsService);
		internetRadioSettingsController.setViewName("internetRadioSettings");
		passwordSettingsController.setSuccessView("passwordSettings");
		passwordSettingsController.setCommandClass(net.sourceforge.subsonic.command.PasswordSettingsCommand.class);
		passwordSettingsController.setSecurityService(securityService);
		passwordSettingsController.setValidator(passwordSettingsValidator);
		passwordSettingsController.setSessionForm(true);
		passwordSettingsController.setFormView("passwordSettings");
		accessDecisionManager.setAllowIfAllAbstainDecisions(false);
		accessDecisionManager.setDecisionVoters(Arrays.asList(inline_bean_4, inline_bean_5));
		importPlaylistController.setSecurityService(securityService);
		importPlaylistController.setPlaylistService(playlistService);
		importPlaylistController.setViewName("importPlaylist");
		avatarDao.setDaoHelper(daoHelper);
		albumDao.setDaoHelper(daoHelper);
		mediaFileDao.setDaoHelper(daoHelper);
		m3uController.setPlayerService(playerService);
		m3uController.setSettingsService(settingsService);
		m3uController.setTranscodingService(transcodingService);
		viewResolver.setViewClass(org.springframework.web.servlet.view.JstlView.class);
		viewResolver.setPrefix("/WEB-INF/jsp/");
		viewResolver.setSuffix(".jsp");
		authenticationProcessingFilter.setRememberMeServices(rememberMeServices);
		authenticationProcessingFilter.setFilterProcessesUrl("/j_acegi_security_check");
		authenticationProcessingFilter.setAlwaysUseDefaultTargetUrl(true);
		authenticationProcessingFilter.setDefaultTargetUrl("/");
		authenticationProcessingFilter.setAuthenticationManager(authenticationManager);
		authenticationProcessingFilter.setAuthenticationFailureUrl("/login.view?error");
		allmusicController.setViewName("allmusic");
		final Ehcache mediaFileMemoryCache = cacheFactory.getCache("mediaFileMemoryCache");
		downloadController.setPlayerService(playerService);
		downloadController.setSettingsService(settingsService);
		downloadController.setStatusService(statusService);
		downloadController.setSecurityService(securityService);
		downloadController.setMediaFileService(mediaFileService);
		downloadController.setPlaylistService(playlistService);
		restController.setBookmarkDao(bookmarkDao);
		restController.setLyricsService(ajaxLyricsService);
		restController.setSecurityService(securityService);
		restController.setPodcastService(podcastService);
		restController.setMusicIndexService(musicIndexService);
		restController.setArtistDao(artistDao);
		restController.setStreamController(streamController);
		restController.setUserSettingsController(userSettingsController);
		restController.setPlayQueueDao(playQueueDao);
		restController.setSearchService(searchService);
		restController.setDownloadController(downloadController);
		restController.setPlayQueueService(ajaxPlayQueueService);
		restController.setCoverArtController(coverArtController);
		restController.setHlsController(hlsController);
		restController.setAvatarController(avatarController);
		restController.setPlayerService(playerService);
		restController.setAudioScrobblerService(audioScrobblerService);
		restController.setTranscodingService(transcodingService);
		restController.setShareService(shareService);
		restController.setAlbumDao(albumDao);
		restController.setJukeboxService(jukeboxService);
		restController.setMediaFileDao(mediaFileDao);
		restController.setSettingsService(settingsService);
		restController.setLeftController(leftController);
		restController.setStatusService(statusService);
		restController.setRatingService(ratingService);
		restController.setMediaFileService(mediaFileService);
		restController.setChatService(ajaxChatService);
		restController.setPlaylistService(playlistService);
		restController.setLastFmService(lastFmService);
		wapController.setPlayerService(playerService);
		wapController.setSearchService(searchService);
		wapController.setSettingsService(settingsService);
		wapController.setSecurityService(securityService);
		wapController.setMediaFileService(mediaFileService);
		wapController.setPlaylistService(playlistService);
		wapController.setMusicIndexService(musicIndexService);
		setMusicFileInfoController.setMediaFileService(mediaFileService);
		playlistService.setSecurityService(securityService);
		playlistService.setPlaylistDao(playlistDao);
		playlistService.setMediaFileDao(mediaFileDao);
		playlistService.setMediaFileService(mediaFileService);
		playlistService.setSettingsService(settingsService);
		inline_bean_1.setForceHttps(false);
		inline_bean_1.setLoginFormUrl("/login.view?");
		inline_bean_2.setErrorPage("/accessDenied.view");
		inline_bean_3.setRealmName("Subsonic");
		inline_bean_6.setKey("subsonic");
		inline_bean_7.setKey("subsonic");
		inline_bean_9.setTranscodingService(transcodingService);
		daoAuthenticationProvider.setUserDetailsService(securityService);
		daoAuthenticationProvider.setUserCache(userCacheWrapper);
		podcastReceiverController.setSecurityService(securityService);
		podcastReceiverController.setSettingsService(settingsService);
		podcastReceiverController.setPodcastService(podcastService);
		podcastReceiverController.setViewName("podcastReceiver");
		topController.setSecurityService(securityService);
		topController.setSettingsService(settingsService);
		topController.setViewName("top");
		filterChainProxy.setFilterInvocationDefinitionSource(null);
		podcastDao.setDaoHelper(daoHelper);
		artistDao.setDaoHelper(daoHelper);
		inline_bean_11.setFrame("playQueue");
		inline_bean_11.setView("playQueue.view?");
		userDetailsServiceBasedAuthoritiesPopulator.setUserDetailsService(securityService);
		ratingService.setRatingDao(musicFileInfoDao);
		ratingService.setSecurityService(securityService);
		ratingService.setMediaFileService(mediaFileService);
		streamController.setPlayerService(playerService);
		streamController.setAudioScrobblerService(audioScrobblerService);
		streamController.setTranscodingService(transcodingService);
		streamController.setSearchService(searchService);
		streamController.setSettingsService(settingsService);
		streamController.setStatusService(statusService);
		streamController.setSecurityService(securityService);
		streamController.setMediaFileService(mediaFileService);
		streamController.setPlaylistService(playlistService);
		anonymousProcessingFilter.setUserAttribute(null);
		anonymousProcessingFilter.setKey("subsonic");
		sonosService.setSecurityService(securityService);
		sonosService.setUpnpService(upnpService);
		sonosService.setMediaFileService(mediaFileService);
		sonosService.setSonosHelper(sonosHelper);
		sonosService.setSettingsService(settingsService);
		playlistsController.setSecurityService(securityService);
		playlistsController.setPlaylistService(playlistService);
		playlistsController.setViewName("playlists");
		avatarController.setSettingsService(settingsService);
		playerService.setPlayerDao(playerDao);
		playerService.setSecurityService(securityService);
		playerService.setTranscodingService(transcodingService);
		playerService.setStatusService(statusService);
		transcodingService.setPlayerService(playerService);
		transcodingService.setSettingsService(settingsService);
		transcodingService.setTranscodingDao(transcodingDao);
		shareSettingsController.setSecurityService(securityService);
		shareSettingsController.setSettingsService(settingsService);
		shareSettingsController.setMediaFileService(mediaFileService);
		shareSettingsController.setShareService(shareService);
		shareSettingsController.setViewName("shareSettings");
		dbController.setDaoHelper(daoHelper);
		dbController.setViewName("db");
		urlMapping.setMappings(null);
		urlMapping.setAlwaysUseFullPath(true);
		jukeboxService.setAudioScrobblerService(audioScrobblerService);
		jukeboxService.setTranscodingService(transcodingService);
		jukeboxService.setSettingsService(settingsService);
		jukeboxService.setStatusService(statusService);
		jukeboxService.setSecurityService(securityService);
		jukeboxService.setMediaFileService(mediaFileService);
		mainController.setPlayerService(playerService);
		mainController.setSettingsService(settingsService);
		mainController.setSecurityService(securityService);
		mainController.setRatingService(ratingService);
		mainController.setMediaFileService(mediaFileService);
		mainController.setAdService(adService);
		upnpService.setVersionService(versionService);
		upnpService.setFolderBasedContentDirectory(folderBasedContentDirectory);
		upnpService.setSettingsService(settingsService);
		sonosSettingsController.setSettingsService(settingsService);
		sonosSettingsController.setSonosService(sonosService);
		sonosSettingsController.setViewName("sonosSettings");
		messageSource.setBasename("net.sourceforge.subsonic.i18n.ResourceBundle");
		searchController.setPlayerService(playerService);
		searchController.setSearchService(searchService);
		searchController.setSuccessView("search");
		searchController.setSettingsService(settingsService);
		searchController.setCommandClass(net.sourceforge.subsonic.command.SearchCommand.class);
		searchController.setSecurityService(securityService);
		searchController.setFormView("search");
		userSettingsValidator.setSecurityService(securityService);
		userSettingsValidator.setSettingsService(settingsService);
		helpController.setSettingsService(settingsService);
		helpController.setVersionService(versionService);
		helpController.setViewName("help");
		themeResolver.setSecurityService(securityService);
		themeResolver.setSettingsService(settingsService);
		podcastController.setPlaylistService(playlistService);
		podcastController.setSecurityService(securityService);
		podcastController.setSettingsService(settingsService);
		podcastController.setViewName("podcast");
		statusService.setMediaFileService(mediaFileService);
		mediaScannerService.setAlbumDao(albumDao);
		mediaScannerService.setSearchService(searchService);
		mediaScannerService.setMediaFileDao(mediaFileDao);
		mediaScannerService.setSettingsService(settingsService);
		mediaScannerService.setMediaFileService(mediaFileService);
		mediaScannerService.setPlaylistService(playlistService);
		mediaScannerService.setArtistDao(artistDao);
		generalSettingsController.setSuccessView("generalSettings");
		generalSettingsController.setSettingsService(settingsService);
		generalSettingsController.setCommandClass(net.sourceforge.subsonic.command.GeneralSettingsCommand.class);
		generalSettingsController.setFormView("generalSettings");
		mediaFileService.setAlbumDao(albumDao);
		mediaFileService.setMediaFileDao(mediaFileDao);
		mediaFileService.setSettingsService(settingsService);
		mediaFileService.setSecurityService(securityService);
		mediaFileService.setMetaDataParserFactory(metaDataParserFactory);
		mediaFileService.setMediaFileMemoryCache(mediaFileMemoryCache);
		podcastSettingsController.setPodcastService(podcastService);
		podcastSettingsController.setSuccessView("podcastSettings");
		podcastSettingsController.setSettingsService(settingsService);
		podcastSettingsController.setCommandClass(net.sourceforge.subsonic.command.PodcastSettingsCommand.class);
		podcastSettingsController.setFormView("podcastSettings");
		videoPlayerController.setSecurityService(securityService);
		videoPlayerController.setSettingsService(settingsService);
		videoPlayerController.setMediaFileService(mediaFileService);
		videoPlayerController.setViewName("videoPlayer");
		videoPlayerController.setPlayerService(playerService);
		ldapAuthenticationProvider.setUserCache(userCacheWrapper);
		bookmarkDao.setDaoHelper(daoHelper);
		nowPlayingController.setPlayerService(playerService);
		nowPlayingController.setMediaFileService(mediaFileService);
		nowPlayingController.setStatusService(statusService);
		basicProcessingFilterEntryPoint.setRealmName("Subsonic");
		userCacheWrapper.setCache(userCache);
		authenticationManager.setProviders(Arrays.<Object> asList(daoAuthenticationProvider, ldapAuthenticationProvider, inline_bean_6, inline_bean_7));
		premiumController.setSuccessView("premium");
		premiumController.setSettingsService(settingsService);
		premiumController.setCommandClass(net.sourceforge.subsonic.command.PremiumCommand.class);
		premiumController.setSecurityService(securityService);
		premiumController.setValidator(premiumValidator);
		premiumController.setFormView("premium");
		playlistController.setSecurityService(securityService);
		playlistController.setPlaylistService(playlistService);
		playlistController.setSettingsService(settingsService);
		playlistController.setViewName("playlist");
		playlistController.setPlayerService(playerService);
		transcodingDao.setDaoHelper(daoHelper);
		musicFolderDao.setDaoHelper(daoHelper);
		userSettingsController.setTranscodingService(transcodingService);
		userSettingsController.setSuccessView("userSettings");
		userSettingsController.setSettingsService(settingsService);
		userSettingsController.setCommandClass(net.sourceforge.subsonic.command.UserSettingsCommand.class);
		userSettingsController.setSecurityService(securityService);
		userSettingsController.setValidator(userSettingsValidator);
		userSettingsController.setSessionForm(true);
		userSettingsController.setFormView("userSettings");
		sonosHelper.setPlayerService(playerService);
		sonosHelper.setTranscodingService(transcodingService);
		sonosHelper.setSearchService(searchService);
		sonosHelper.setMediaFileDao(mediaFileDao);
		sonosHelper.setSettingsService(settingsService);
		sonosHelper.setPodcastService(podcastService);
		sonosHelper.setRatingService(ratingService);
		sonosHelper.setMediaFileService(mediaFileService);
		sonosHelper.setLastFmService(lastFmService);
		sonosHelper.setPlaylistService(playlistService);
		sonosHelper.setMusicIndexService(musicIndexService);
		ajaxNowPlayingService.setMediaScannerService(mediaScannerService);
		ajaxNowPlayingService.setPlayerService(playerService);
		ajaxNowPlayingService.setSettingsService(settingsService);
		ajaxNowPlayingService.setStatusService(statusService);
		editTagsController.setMetaDataParserFactory(metaDataParserFactory);
		editTagsController.setMediaFileService(mediaFileService);
		editTagsController.setViewName("editTags");
		lyricsController.setViewName("lyrics");
		multiController.setSecurityService(securityService);
		multiController.setPlaylistService(playlistService);
		multiController.setSettingsService(settingsService);
		ajaxPlayQueueService.setPlayerService(playerService);
		ajaxPlayQueueService.setTranscodingService(transcodingService);
		ajaxPlayQueueService.setPlayQueueDao(playQueueDao);
		ajaxPlayQueueService.setSearchService(searchService);
		ajaxPlayQueueService.setJukeboxService(jukeboxService);
		ajaxPlayQueueService.setMediaFileDao(mediaFileDao);
		ajaxPlayQueueService.setSettingsService(settingsService);
		ajaxPlayQueueService.setSecurityService(securityService);
		ajaxPlayQueueService.setRatingService(ratingService);
		ajaxPlayQueueService.setMediaFileService(mediaFileService);
		ajaxPlayQueueService.setPlaylistService(playlistService);
		ajaxPlayQueueService.setLastFmService(lastFmService);
		playQueueController.setSecurityService(securityService);
		playQueueController.setPlayerService(playerService);
		playQueueController.setSettingsService(settingsService);
		playQueueController.setViewName("playQueue");
		avatarUploadController.setSecurityService(securityService);
		avatarUploadController.setSettingsService(settingsService);
		avatarUploadController.setViewName("avatarUploadResult");
		playerDao.setDaoHelper(daoHelper);
		audioScrobblerService.setSettingsService(settingsService);
		ajaxStarService.setSecurityService(securityService);
		ajaxStarService.setMediaFileDao(mediaFileDao);
		shareService.setSecurityService(securityService);
		shareService.setShareDao(shareDao);
		shareService.setSettingsService(settingsService);
		shareService.setMediaFileService(mediaFileService);
		playlistDao.setDaoHelper(daoHelper);
		coverArtController.setTranscodingService(transcodingService);
		coverArtController.setAlbumDao(albumDao);
		coverArtController.setSettingsService(settingsService);
		coverArtController.setMediaFileService(mediaFileService);
		coverArtController.setPlaylistService(playlistService);
		coverArtController.setArtistDao(artistDao);
		folderBasedContentDirectory.setPlaylistService(playlistService);
		folderBasedContentDirectory.setPlayerService(playerService);
		folderBasedContentDirectory.setSettingsService(settingsService);
		folderBasedContentDirectory.setTranscodingService(transcodingService);
		folderBasedContentDirectory.setMediaFileService(mediaFileService);
		homeController.setViewName("home");
		homeController.setMediaScannerService(mediaScannerService);
		homeController.setSettingsService(settingsService);
		homeController.setSecurityService(securityService);
		homeController.setRatingService(ratingService);
		homeController.setMediaFileService(mediaFileService);
		homeController.setSearchService(searchService);
		filterInvocationInterceptor.setAccessDecisionManager(accessDecisionManager);
		filterInvocationInterceptor.setAlwaysReauthenticate(true);
		filterInvocationInterceptor.setAuthenticationManager(authenticationManager);
		filterInvocationInterceptor.setObjectDefinitionSource(null);
		themeSource.setBasenamePrefix("net.sourceforge.subsonic.theme.");
		themeSource.setSettingsService(settingsService);
		ajaxTagService.setMetaDataParserFactory(metaDataParserFactory);
		ajaxTagService.setMediaFileService(mediaFileService);
		uploadController.setSecurityService(securityService);
		uploadController.setPlayerService(playerService);
		uploadController.setSettingsService(settingsService);
		uploadController.setStatusService(statusService);
		uploadController.setViewName("upload");
		starredController.setPlayerService(playerService);
		starredController.setViewName("starred");
		starredController.setMediaFileDao(mediaFileDao);
		starredController.setSettingsService(settingsService);
		starredController.setSecurityService(securityService);
		starredController.setMediaFileService(mediaFileService);
		metaDataParserFactory.setParsers(Arrays.asList(inline_bean_8, inline_bean_9, inline_bean_10));
		shareDao.setDaoHelper(daoHelper);
		hlsController.setSecurityService(securityService);
		hlsController.setPlayerService(playerService);
		hlsController.setMediaFileService(mediaFileService);
		lastFmService.setMediaFileDao(mediaFileDao);
		lastFmService.setMediaFileService(mediaFileService);
		lastFmService.setArtistDao(artistDao);
		exceptionTranslationFilter.setAuthenticationEntryPoint(inline_bean_1);
		exceptionTranslationFilter.setAccessDeniedHandler(inline_bean_2);
		rememberMeServices.setUserDetailsService(securityService);
		rememberMeServices.setTokenValiditySeconds(31536000);
		rememberMeServices.setKey("subsonic");
		dlnaSettingsController.setUpnpService(upnpService);
		dlnaSettingsController.setSettingsService(settingsService);
		dlnaSettingsController.setViewName("dlnaSettings");
		musicFolderSettingsController.setAlbumDao(albumDao);
		musicFolderSettingsController.setMediaScannerService(mediaScannerService);
		musicFolderSettingsController.setSuccessView("musicFolderSettings");
		musicFolderSettingsController.setMediaFileDao(mediaFileDao);
		musicFolderSettingsController.setSettingsService(settingsService);
		musicFolderSettingsController.setCommandClass(net.sourceforge.subsonic.command.MusicFolderSettingsCommand.class);
		musicFolderSettingsController.setFormView("musicFolderSettings");
		musicFolderSettingsController.setArtistDao(artistDao);

		externalPlayerController.setSettingsService(settingsService);
		externalPlayerController.setMediaFileService(mediaFileService);
		externalPlayerController.setShareDao(shareDao);
		externalPlayerController.setViewName("externalPlayer");
		externalPlayerController.setPlayerService(playerService);
		rememberMeProcessingFilter.setRememberMeServices(rememberMeServices);
		rememberMeProcessingFilter.setAuthenticationManager(authenticationManager);
		securityService.setUserCache(userCache);
		securityService.setSettingsService(settingsService);
		securityService.setUserDao(userDao);
		musicFileInfoDao.setDaoHelper(daoHelper);
		ajaxTransferServiceSecure.setInterceptorNames(new String[] { "ajaxServiceInterceptor" });
		ajaxTransferServiceSecure.setTarget(ajaxTransferService);
		podcastReceiverAdminController.setPodcastService(podcastService);
		statusChartController.setStatusService(statusService);
		playerSettingsController.setPlayerService(playerService);
		playerSettingsController.setTranscodingService(transcodingService);
		playerSettingsController.setSuccessView("playerSettings");
		playerSettingsController.setCommandClass(net.sourceforge.subsonic.command.PlayerSettingsCommand.class);
		playerSettingsController.setSecurityService(securityService);
		playerSettingsController.setFormView("playerSettings");
		musicIndexService.setSettingsService(settingsService);
		musicIndexService.setMediaFileService(mediaFileService);
		ajaxChatService.setSecurityService(securityService);
		userChartController.setSecurityService(securityService);
		rightController.setSecurityService(securityService);
		rightController.setVersionService(versionService);
		rightController.setSettingsService(settingsService);
		rightController.setViewName("right");
		setRatingController.setSecurityService(securityService);
		setRatingController.setRatingService(ratingService);
		setRatingController.setMediaFileService(mediaFileService);
		networkSettingsController.setNetworkService(networkService);
		networkSettingsController.setSuccessView("networkSettings");
		networkSettingsController.setSettingsService(settingsService);
		networkSettingsController.setCommandClass(net.sourceforge.subsonic.command.NetworkSettingsCommand.class);
		networkSettingsController.setFormView("networkSettings");
		advancedSettingsController.setSuccessView("advancedSettings");
		advancedSettingsController.setSettingsService(settingsService);
		advancedSettingsController.setCommandClass(net.sourceforge.subsonic.command.AdvancedSettingsCommand.class);
		advancedSettingsController.setFormView("advancedSettings");
		restRequestParameterProcessingFilter.setSettingsService(settingsService);
		restRequestParameterProcessingFilter.setAuthenticationManager(authenticationManager);
		randomPlayQueueController.setPlayerService(playerService);
		randomPlayQueueController.setViewName("reload");
		randomPlayQueueController.setSearchService(searchService);
		randomPlayQueueController.setReloadFrames(Arrays.asList(inline_bean_11, inline_bean_12));
		randomPlayQueueController.setSettingsService(settingsService);
		randomPlayQueueController.setSecurityService(securityService);
		ajaxTagServiceSecure.setInterceptorNames(new String[] { "ajaxServiceInterceptor" });
		ajaxTagServiceSecure.setTarget(ajaxTagService);
		settingsController.setSecurityService(securityService);
		bindAuthenticator.setSecurityService(securityService);
		bindAuthenticator.setSettingsService(settingsService);
		internetRadioDao.setDaoHelper(daoHelper);
		transcodingSettingsController.setSettingsService(settingsService);
		transcodingSettingsController.setTranscodingService(transcodingService);
		transcodingSettingsController.setViewName("transcodingSettings");
		changeCoverArtController.setMediaFileService(mediaFileService);
		changeCoverArtController.setViewName("changeCoverArt");
		settingsService.setAvatarDao(avatarDao);
		settingsService.setMusicFolderDao(musicFolderDao);
		settingsService.setVersionService(versionService);
		settingsService.setInternetRadioDao(internetRadioDao);
		settingsService.setUserDao(userDao);
		networkService.setUpnpService(upnpService);
		networkService.setSettingsService(settingsService);
		inline_bean_12.setFrame("main");
		inline_bean_12.setView("more.view");
		ajaxCoverArtService.setSecurityService(securityService);
		ajaxCoverArtService.setMediaFileService(mediaFileService);
		leftController.setPlayerService(playerService);
		leftController.setViewName("left");
		leftController.setMediaScannerService(mediaScannerService);
		leftController.setSettingsService(settingsService);
		leftController.setSecurityService(securityService);
		leftController.setMusicIndexService(musicIndexService);
		adService.setAdInterval(8);
		statusController.setStatusService(statusService);
		statusController.setViewName("status");
		basicExceptionTranslationFilter.setAuthenticationEntryPoint(inline_bean_3);

		javax.servlet.jsp.JspFactory.setDefaultFactory(new SimpleJspFactory());
		final MyContext context = new MyContext();

		// subsonic
		final SubsonicModel serv_0 = new SubsonicModel(mainController, playlistController, playlistsController, helpController, lyricsController, leftController, rightController,
				statusController, moreController, uploadController, importPlaylistController, multiController, setMusicFileInfoController, shareManagementController, setRatingController,
				topController, randomPlayQueueController, changeCoverArtController, videoPlayerController, nowPlayingController, starredController, searchController, settingsController,
				playerSettingsController, dlnaSettingsController, sonosSettingsController, shareSettingsController, musicFolderSettingsController, networkSettingsController,
				transcodingSettingsController, internetRadioSettingsController, podcastSettingsController, generalSettingsController, advancedSettingsController,
				personalSettingsController, avatarUploadController, userSettingsController, passwordSettingsController, allmusicController, homeController, editTagsController,
				playQueueController, coverArtController, avatarController, proxyController, statusChartController, userChartController, downloadController, premiumController,
				dbController, podcastReceiverController, podcastReceiverAdminController, podcastController, downloadController, wapController, restController, m3uController,
				streamController, hlsController, externalPlayerController);
		serv_0.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_0;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.playlist_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.playlist_jsp serv_3 = new net.sourceforge.subsonic.WEB_002dINF.jsp.playlist_jsp();
		serv_3.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_3;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.settingsHeader_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.settingsHeader_jsp serv_4 = new net.sourceforge.subsonic.WEB_002dINF.jsp.settingsHeader_jsp();
		serv_4.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_4;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.db_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.db_jsp serv_5 = new net.sourceforge.subsonic.WEB_002dINF.jsp.db_jsp();
		serv_5.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_5;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.transcodingSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.transcodingSettings_jsp serv_6 = new net.sourceforge.subsonic.WEB_002dINF.jsp.transcodingSettings_jsp();
		serv_6.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_6;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.podcastReceiver_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.podcastReceiver_jsp serv_7 = new net.sourceforge.subsonic.WEB_002dINF.jsp.podcastReceiver_jsp();
		serv_7.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_7;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.generalSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.generalSettings_jsp serv_8 = new net.sourceforge.subsonic.WEB_002dINF.jsp.generalSettings_jsp();
		serv_8.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_8;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.videoMain_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.videoMain_jsp serv_9 = new net.sourceforge.subsonic.WEB_002dINF.jsp.videoMain_jsp();
		serv_9.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_9;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.shareSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.shareSettings_jsp serv_10 = new net.sourceforge.subsonic.WEB_002dINF.jsp.shareSettings_jsp();
		serv_10.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_10;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.playlists_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.playlists_jsp serv_11 = new net.sourceforge.subsonic.WEB_002dINF.jsp.playlists_jsp();
		serv_11.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_11;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.right_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.right_jsp serv_12 = new net.sourceforge.subsonic.WEB_002dINF.jsp.right_jsp();
		serv_12.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_12;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayer_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayer_jsp serv_13 = new net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayer_jsp();
		serv_13.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_13;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.include_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.include_jsp serv_14 = new net.sourceforge.subsonic.WEB_002dINF.jsp.include_jsp();
		serv_14.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_14;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.internetRadioSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.internetRadioSettings_jsp serv_15 = new net.sourceforge.subsonic.WEB_002dINF.jsp.internetRadioSettings_jsp();
		serv_15.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_15;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.createShare_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.createShare_jsp serv_16 = new net.sourceforge.subsonic.WEB_002dINF.jsp.createShare_jsp();
		serv_16.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_16;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.passwordSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.passwordSettings_jsp serv_17 = new net.sourceforge.subsonic.WEB_002dINF.jsp.passwordSettings_jsp();
		serv_17.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_17;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.playQueueCast_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.playQueueCast_jsp serv_18 = new net.sourceforge.subsonic.WEB_002dINF.jsp.playQueueCast_jsp();
		serv_18.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_18;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.status_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.status_jsp serv_19 = new net.sourceforge.subsonic.WEB_002dINF.jsp.status_jsp();
		serv_19.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_19;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.top_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.top_jsp serv_20 = new net.sourceforge.subsonic.WEB_002dINF.jsp.top_jsp();
		serv_20.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_20;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.avatarUploadResult_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.avatarUploadResult_jsp serv_21 = new net.sourceforge.subsonic.WEB_002dINF.jsp.avatarUploadResult_jsp();
		serv_21.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_21;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.importPlaylist_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.importPlaylist_jsp serv_22 = new net.sourceforge.subsonic.WEB_002dINF.jsp.importPlaylist_jsp();
		serv_22.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_22;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.playerSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.playerSettings_jsp serv_23 = new net.sourceforge.subsonic.WEB_002dINF.jsp.playerSettings_jsp();
		serv_23.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_23;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.dlnaSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.dlnaSettings_jsp serv_24 = new net.sourceforge.subsonic.WEB_002dINF.jsp.dlnaSettings_jsp();
		serv_24.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_24;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.helpToolTip_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.helpToolTip_jsp serv_25 = new net.sourceforge.subsonic.WEB_002dINF.jsp.helpToolTip_jsp();
		serv_25.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_25;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.help_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.help_jsp serv_26 = new net.sourceforge.subsonic.WEB_002dINF.jsp.help_jsp();
		serv_26.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_26;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.starred_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.starred_jsp serv_27 = new net.sourceforge.subsonic.WEB_002dINF.jsp.starred_jsp();
		serv_27.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_27;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.podcastSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.podcastSettings_jsp serv_28 = new net.sourceforge.subsonic.WEB_002dINF.jsp.podcastSettings_jsp();
		serv_28.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_28;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayerCast_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayerCast_jsp serv_29 = new net.sourceforge.subsonic.WEB_002dINF.jsp.videoPlayerCast_jsp();
		serv_29.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_29;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.left_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.left_jsp serv_30 = new net.sourceforge.subsonic.WEB_002dINF.jsp.left_jsp();
		serv_30.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_30;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.playButtons_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.playButtons_jsp serv_31 = new net.sourceforge.subsonic.WEB_002dINF.jsp.playButtons_jsp();
		serv_31.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_31;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.wap.playlist_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.playlist_jsp serv_32 = new net.sourceforge.subsonic.WEB_002dINF.jsp.wap.playlist_jsp();
		serv_32.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_32;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.wap.settings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.settings_jsp serv_33 = new net.sourceforge.subsonic.WEB_002dINF.jsp.wap.settings_jsp();
		serv_33.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_33;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.wap.search_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.search_jsp serv_34 = new net.sourceforge.subsonic.WEB_002dINF.jsp.wap.search_jsp();
		serv_34.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_34;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.wap.head_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.head_jsp serv_35 = new net.sourceforge.subsonic.WEB_002dINF.jsp.wap.head_jsp();
		serv_35.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_35;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.wap.loadPlaylist_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.loadPlaylist_jsp serv_36 = new net.sourceforge.subsonic.WEB_002dINF.jsp.wap.loadPlaylist_jsp();
		serv_36.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_36;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.wap.searchResult_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.searchResult_jsp serv_37 = new net.sourceforge.subsonic.WEB_002dINF.jsp.wap.searchResult_jsp();
		serv_37.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_37;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.wap.index_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.index_jsp serv_38 = new net.sourceforge.subsonic.WEB_002dINF.jsp.wap.index_jsp();
		serv_38.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_38;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.wap.browse_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.wap.browse_jsp serv_39 = new net.sourceforge.subsonic.WEB_002dINF.jsp.wap.browse_jsp();
		serv_39.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_39;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.rating_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.rating_jsp serv_40 = new net.sourceforge.subsonic.WEB_002dINF.jsp.rating_jsp();
		serv_40.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_40;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.albumMain_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.albumMain_jsp serv_41 = new net.sourceforge.subsonic.WEB_002dINF.jsp.albumMain_jsp();
		serv_41.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_41;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.notFound_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.notFound_jsp serv_42 = new net.sourceforge.subsonic.WEB_002dINF.jsp.notFound_jsp();
		serv_42.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_42;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.upload_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.upload_jsp serv_43 = new net.sourceforge.subsonic.WEB_002dINF.jsp.upload_jsp();
		serv_43.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_43;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.premium_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.premium_jsp serv_44 = new net.sourceforge.subsonic.WEB_002dINF.jsp.premium_jsp();
		serv_44.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_44;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.userSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.userSettings_jsp serv_45 = new net.sourceforge.subsonic.WEB_002dINF.jsp.userSettings_jsp();
		serv_45.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_45;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.changeCoverArt_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.changeCoverArt_jsp serv_46 = new net.sourceforge.subsonic.WEB_002dINF.jsp.changeCoverArt_jsp();
		serv_46.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_46;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.musicFolderSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.musicFolderSettings_jsp serv_47 = new net.sourceforge.subsonic.WEB_002dINF.jsp.musicFolderSettings_jsp();
		serv_47.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_47;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.rest.videoPlayer_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.rest.videoPlayer_jsp serv_48 = new net.sourceforge.subsonic.WEB_002dINF.jsp.rest.videoPlayer_jsp();
		serv_48.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_48;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.editTags_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.editTags_jsp serv_49 = new net.sourceforge.subsonic.WEB_002dINF.jsp.editTags_jsp();
		serv_49.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_49;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.licenseNotice_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.licenseNotice_jsp serv_50 = new net.sourceforge.subsonic.WEB_002dINF.jsp.licenseNotice_jsp();
		serv_50.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_50;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.networkSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.networkSettings_jsp serv_51 = new net.sourceforge.subsonic.WEB_002dINF.jsp.networkSettings_jsp();
		serv_51.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_51;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.viewSelector_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.viewSelector_jsp serv_52 = new net.sourceforge.subsonic.WEB_002dINF.jsp.viewSelector_jsp();
		serv_52.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_52;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.search_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.search_jsp serv_53 = new net.sourceforge.subsonic.WEB_002dINF.jsp.search_jsp();
		serv_53.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_53;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.home_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.home_jsp serv_54 = new net.sourceforge.subsonic.WEB_002dINF.jsp.home_jsp();
		serv_54.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_54;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.externalPlayer_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.externalPlayer_jsp serv_55 = new net.sourceforge.subsonic.WEB_002dINF.jsp.externalPlayer_jsp();
		serv_55.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_55;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.head_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.head_jsp serv_56 = new net.sourceforge.subsonic.WEB_002dINF.jsp.head_jsp();
		serv_56.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_56;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.lyrics_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.lyrics_jsp serv_57 = new net.sourceforge.subsonic.WEB_002dINF.jsp.lyrics_jsp();
		serv_57.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_57;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.coverArt_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.coverArt_jsp serv_58 = new net.sourceforge.subsonic.WEB_002dINF.jsp.coverArt_jsp();
		serv_58.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_58;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.login_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.login_jsp serv_59 = new net.sourceforge.subsonic.WEB_002dINF.jsp.login_jsp();
		serv_59.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_59;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.personalSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.personalSettings_jsp serv_60 = new net.sourceforge.subsonic.WEB_002dINF.jsp.personalSettings_jsp();
		serv_60.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_60;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.recover_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.recover_jsp serv_61 = new net.sourceforge.subsonic.WEB_002dINF.jsp.recover_jsp();
		serv_61.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_61;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.sonosSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.sonosSettings_jsp serv_62 = new net.sourceforge.subsonic.WEB_002dINF.jsp.sonosSettings_jsp();
		serv_62.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_62;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.homePager_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.homePager_jsp serv_63 = new net.sourceforge.subsonic.WEB_002dINF.jsp.homePager_jsp();
		serv_63.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_63;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.allmusic_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.allmusic_jsp serv_64 = new net.sourceforge.subsonic.WEB_002dINF.jsp.allmusic_jsp();
		serv_64.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_64;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.index_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.index_jsp serv_65 = new net.sourceforge.subsonic.WEB_002dINF.jsp.index_jsp();
		serv_65.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_65;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.jquery_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.jquery_jsp serv_66 = new net.sourceforge.subsonic.WEB_002dINF.jsp.jquery_jsp();
		serv_66.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_66;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.playQueue_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.playQueue_jsp serv_67 = new net.sourceforge.subsonic.WEB_002dINF.jsp.playQueue_jsp();
		serv_67.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_67;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.gettingStarted_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.gettingStarted_jsp serv_68 = new net.sourceforge.subsonic.WEB_002dINF.jsp.gettingStarted_jsp();
		serv_68.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_68;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.advancedSettings_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.advancedSettings_jsp serv_69 = new net.sourceforge.subsonic.WEB_002dINF.jsp.advancedSettings_jsp();
		serv_69.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_69;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.podcast_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.podcast_jsp serv_70 = new net.sourceforge.subsonic.WEB_002dINF.jsp.podcast_jsp();
		serv_70.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_70;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.accessDenied_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.accessDenied_jsp serv_71 = new net.sourceforge.subsonic.WEB_002dINF.jsp.accessDenied_jsp();
		serv_71.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_71;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.test_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.test_jsp serv_72 = new net.sourceforge.subsonic.WEB_002dINF.jsp.test_jsp();
		serv_72.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_72;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.more_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.more_jsp serv_73 = new net.sourceforge.subsonic.WEB_002dINF.jsp.more_jsp();
		serv_73.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_73;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.reload_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.reload_jsp serv_74 = new net.sourceforge.subsonic.WEB_002dINF.jsp.reload_jsp();
		serv_74.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_74;
		// net.sourceforge.subsonic.WEB_002dINF.jsp.artistMain_jsp
		final net.sourceforge.subsonic.WEB_002dINF.jsp.artistMain_jsp serv_75 = new net.sourceforge.subsonic.WEB_002dINF.jsp.artistMain_jsp();
		serv_75.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_75;
		// net.sourceforge.subsonic.index_jsp
		final net.sourceforge.subsonic.index_jsp serv_76 = new net.sourceforge.subsonic.index_jsp();
		serv_76.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_76;
		// net.sourceforge.subsonic.error_jsp
		final net.sourceforge.subsonic.error_jsp serv_77 = new net.sourceforge.subsonic.error_jsp();
		serv_77.init(new SimpleServletConfig(context));
		context.servlets[0] = serv_77;

		final net.sourceforge.subsonic.filter.BootstrapVerificationFilter filter_0 = new net.sourceforge.subsonic.filter.BootstrapVerificationFilter();
		filter_0.init(new SimpleFilterConfig(context));
		final net.sourceforge.subsonic.filter.ParameterDecodingFilter filter_1 = new net.sourceforge.subsonic.filter.ParameterDecodingFilter();
		filter_1.init(new SimpleFilterConfig(context));
		final net.sourceforge.subsonic.filter.RESTFilter filter_2 = new net.sourceforge.subsonic.filter.RESTFilter();
		filter_2.init(new SimpleFilterConfig(context));
		final net.sourceforge.subsonic.filter.RequestEncodingFilter filter_3 = new net.sourceforge.subsonic.filter.RequestEncodingFilter();
		filter_3.init(new SimpleFilterConfig(context));
		final net.sourceforge.subsonic.filter.ResponseHeaderFilter filter_4 = new net.sourceforge.subsonic.filter.ResponseHeaderFilter();
		filter_4.init(new SimpleFilterConfig(context));
		final DummyAcegiFilter filter_5 = new DummyAcegiFilter(securityService, bindAuthenticator);
		filter_5.init(new SimpleFilterConfig(context));

		while(nondetBool()) {
			final SimpleHttpRequest sr = new SimpleHttpRequest(context);
			final SimpleHttpResponse sp = new SimpleHttpResponse();

			context.notifyRequestInitialized(sr);

			// final AllServletsHandler all_handlers = new AllServletsHandler(serv_0,
			// serv_3, serv_4, serv_5, serv_6, serv_7, serv_8, serv_9, serv_10,
			// serv_11, serv_12, serv_13, serv_14,
			// serv_15, serv_16, serv_17, serv_18, serv_19, serv_20, serv_21, serv_22,
			// serv_23, serv_24, serv_25, serv_26, serv_27, serv_28, serv_29, serv_30,
			// serv_31, serv_32,
			// serv_33, serv_34, serv_35, serv_36, serv_37, serv_38, serv_39, serv_40,
			// serv_41, serv_42, serv_43, serv_44, serv_45, serv_46, serv_47, serv_48,
			// serv_49, serv_50,
			// serv_51, serv_52, serv_53, serv_54, serv_55, serv_56, serv_57, serv_58,
			// serv_59, serv_60, serv_61, serv_62, serv_63, serv_64, serv_65, serv_66,
			// serv_67, serv_68,
			// serv_69, serv_70, serv_71, serv_72, serv_73, serv_74, serv_75, serv_76,
			// serv_77);
			final AllServletsHandler all_handlers = new AllServletsHandler(serv_0);
			final FilterChain_0 temp_0 = new FilterChain_0(filter_5, all_handlers);
			final FilterChain_1 temp_1 = new FilterChain_1(filter_4, temp_0);
			final FilterChain_2 temp_3 = new FilterChain_2(filter_3, temp_1);
			final FilterChain_3 temp_4 = new FilterChain_3(filter_2, temp_3);
			final FilterChain_4 temp_5 = new FilterChain_4(filter_1, temp_4);
			final FilterChain_5 temp_6 = new FilterChain_5(filter_0, temp_5);

			temp_6.doFilter(sr, sp);

			context.notifyRequestDestroyed(sr);
			context.destroySession();
			context.actSession();
			context.passSession();
		}

		filter_0.destroy();
		filter_1.destroy();
		filter_2.destroy();
		filter_3.destroy();
		filter_4.destroy();
		filter_4.destroy();
		filter_5.destroy();
		serv_0.destroy();
		serv_3.destroy();
		serv_4.destroy();
		serv_5.destroy();
		serv_6.destroy();
		serv_7.destroy();
		serv_8.destroy();
		serv_9.destroy();
		serv_10.destroy();
		serv_11.destroy();
		serv_12.destroy();
		serv_13.destroy();
		serv_14.destroy();
		serv_15.destroy();
		serv_16.destroy();
		serv_17.destroy();
		serv_18.destroy();
		serv_19.destroy();
		serv_20.destroy();
		serv_21.destroy();
		serv_22.destroy();
		serv_23.destroy();
		serv_24.destroy();
		serv_25.destroy();
		serv_26.destroy();
		serv_27.destroy();
		serv_28.destroy();
		serv_29.destroy();
		serv_30.destroy();
		serv_31.destroy();
		serv_32.destroy();
		serv_33.destroy();
		serv_34.destroy();
		serv_35.destroy();
		serv_36.destroy();
		serv_37.destroy();
		serv_38.destroy();
		serv_39.destroy();
		serv_40.destroy();
		serv_41.destroy();
		serv_42.destroy();
		serv_43.destroy();
		serv_44.destroy();
		serv_45.destroy();
		serv_46.destroy();
		serv_47.destroy();
		serv_48.destroy();
		serv_49.destroy();
		serv_50.destroy();
		serv_51.destroy();
		serv_52.destroy();
		serv_53.destroy();
		serv_54.destroy();
		serv_55.destroy();
		serv_56.destroy();
		serv_57.destroy();
		serv_58.destroy();
		serv_59.destroy();
		serv_60.destroy();
		serv_61.destroy();
		serv_62.destroy();
		serv_63.destroy();
		serv_64.destroy();
		serv_65.destroy();
		serv_66.destroy();
		serv_67.destroy();
		serv_68.destroy();
		serv_69.destroy();
		serv_70.destroy();
		serv_71.destroy();
		serv_72.destroy();
		serv_73.destroy();
		serv_74.destroy();
		serv_75.destroy();
		serv_76.destroy();
		serv_77.destroy();

		context.notifyContextDestroyed();
	}
}

class AllServletsHandler implements FilterChain {
	private final SubsonicModel sub_handler_0;
	public AllServletsHandler(SubsonicModel serv_0) {
		this.sub_handler_0 = serv_0;
	}

	@Override
	public void doFilter(final ServletRequest r1, final ServletResponse r2) throws IOException, ServletException {
		this.sub_handler_0.service(r1, r2);
	}
}

class FilterChain_5 implements FilterChain {
	private final FilterChain_4 delegate;
	private final net.sourceforge.subsonic.filter.BootstrapVerificationFilter filter;

	FilterChain_5(final net.sourceforge.subsonic.filter.BootstrapVerificationFilter filter, final FilterChain_4 delegate) {
		this.delegate = delegate;
		this.filter = filter;
	}

	@Override
	public void doFilter(final ServletRequest r1, final ServletResponse r2) throws IOException, ServletException {
		this.filter.doFilter(r1, r2, this.delegate);
	}
}

class FilterChain_4 implements FilterChain {
	private final FilterChain_3 delegate;
	private final net.sourceforge.subsonic.filter.ParameterDecodingFilter filter;

	FilterChain_4(final net.sourceforge.subsonic.filter.ParameterDecodingFilter filter, final FilterChain_3 delegate) {
		this.delegate = delegate;
		this.filter = filter;
	}

	@Override
	public void doFilter(final ServletRequest r1, final ServletResponse r2) throws IOException, ServletException {
		this.filter.doFilter(r1, r2, this.delegate);
	}
}

class FilterChain_3 implements FilterChain {
	private final FilterChain_2 delegate;
	private final net.sourceforge.subsonic.filter.RESTFilter filter;

	FilterChain_3(final net.sourceforge.subsonic.filter.RESTFilter filter, final FilterChain_2 delegate) {
		this.delegate = delegate;
		this.filter = filter;
	}

	@Override
	public void doFilter(final ServletRequest r1, final ServletResponse r2) throws IOException, ServletException {
		this.filter.doFilter(r1, r2, this.delegate);
	}
}

class FilterChain_2 implements FilterChain {
	private final FilterChain_1 delegate;
	private final net.sourceforge.subsonic.filter.RequestEncodingFilter filter;

	FilterChain_2(final net.sourceforge.subsonic.filter.RequestEncodingFilter filter, final FilterChain_1 delegate) {
		this.delegate = delegate;
		this.filter = filter;
	}

	@Override
	public void doFilter(final ServletRequest r1, final ServletResponse r2) throws IOException, ServletException {
		this.filter.doFilter(r1, r2, this.delegate);
	}
}

class FilterChain_1 implements FilterChain {
	private final FilterChain_0 delegate;
	private final net.sourceforge.subsonic.filter.ResponseHeaderFilter filter;

	FilterChain_1(final net.sourceforge.subsonic.filter.ResponseHeaderFilter filter, final FilterChain_0 delegate) {
		this.delegate = delegate;
		this.filter = filter;
	}

	@Override
	public void doFilter(final ServletRequest r1, final ServletResponse r2) throws IOException, ServletException {
		this.filter.doFilter(r1, r2, this.delegate);
	}
}

class FilterChain_0 implements FilterChain {
	private final AllServletsHandler delegate;
	private final DummyAcegiFilter filter;

	FilterChain_0(final DummyAcegiFilter filter, final AllServletsHandler delegate) {
		this.delegate = delegate;
		this.filter = filter;
	}

	@Override
	public void doFilter(final ServletRequest r1, final ServletResponse r2) throws IOException, ServletException {
		this.filter.doFilter(r1, r2, this.delegate);
	}
}
