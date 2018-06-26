filters = [
"httpSessionContextIntegrationFilter",
"logoutFilter",
"authenticationProcessingFilter",
"basicProcessingFilter",
"restRequestParameterProcessingFilter",
"securityContextHolderAwareRequestFilter",
"rememberMeProcessingFilter",
"anonymousProcessingFilter",
"exceptionTranslationFilter",
"basicExceptionTranslationFilter",
"f1",
"f2",
"f3",
"f4",
"f5",
]

controllers = [
    "advancedSettingsController",
    "allmusicController",
    "avatarController",
    "avatarUploadController",
    "changeCoverArtController",
    "coverArtController",
    "dbController",
    "dlnaSettingsController",
    "downloadController",
    "editTagsController",
    "externalPlayerController",
    "generalSettingsController",
    "helpController",
    "hlsController",
    "homeController",
    "importPlaylistController",
    "internetRadioSettingsController",
    "leftController",
    "lyricsController",
    "m3uController",
    "mainController",
    "moreController",
    "multiController",
    "musicFolderSettingsController",
    "networkSettingsController",
    "nowPlayingController",
    "passwordSettingsController",
    "personalSettingsController",
    "playerSettingsController",
    "playlistController",
    "playlistsController",
    "playQueueController",
    "podcastController",
    "podcastReceiverAdminController",
    "podcastReceiverController",
    "podcastSettingsController",
    "premiumController",
    "proxyController",
    "randomPlayQueueController",
    "restController",
    "rightController",
    "searchController",
    "setMusicFileInfoController",
    "setRatingController",
    "settingsController",
    "shareManagementController",
    "shareSettingsController",
    "sonosSettingsController",
    "starredController",
    "statusChartController",
    "statusController",
    "streamController",
    "topController",
    "transcodingSettingsController",
    "uploadController",
    "userChartController",
    "userSettingsController",
    "videoPlayerController",
    "wapController"
]

for f in filters:
    print "%s.init(null);" % f

for f in filters:
    print "%s.doFilter(req, resp, null);" % f

i = 0
for c in controllers:
    print "} else if(m == %d) {" % i
    print "ModelAndView mv = %s.handleRequest(req, resp);" % c
    print "render(mv);"
    i+=1
