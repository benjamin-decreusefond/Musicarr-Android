package com.musicarr.android

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musicarr.android.playback.PlayerConnection
import com.musicarr.android.ui.CoverImage
import com.musicarr.android.ui.LocalPlayer
import com.musicarr.android.ui.dpadFocusable
import com.musicarr.android.ui.screens.AlbumScreen
import com.musicarr.android.ui.screens.ArtistScreen
import com.musicarr.android.ui.screens.DownloadsScreen
import com.musicarr.android.ui.screens.HomeScreen
import com.musicarr.android.ui.screens.LibraryScreen
import com.musicarr.android.ui.screens.LoginScreen
import com.musicarr.android.ui.screens.NowPlayingScreen
import com.musicarr.android.ui.screens.PlaylistDetailScreen
import com.musicarr.android.ui.screens.PlaylistsScreen
import com.musicarr.android.ui.screens.SearchScreen
import com.musicarr.android.ui.theme.MusicarrTheme
import kotlinx.coroutines.launch

private data class TopDestination(val route: String, val label: String, val icon: ImageVector)

private val TOP_DESTINATIONS = listOf(
    TopDestination("home", "Home", Icons.Default.Home),
    TopDestination("search", "Search", Icons.Default.Search),
    TopDestination("library", "Library", Icons.Default.LibraryMusic),
    TopDestination("playlists", "Playlists", Icons.Default.QueueMusic),
    TopDestination("downloads", "Downloads", Icons.Default.Download),
)

class MainActivity : ComponentActivity() {
    private var playerConnection: PlayerConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val connection = PlayerConnection(this).also { playerConnection = it }
        // TV launchers (leanback) get side-rail navigation; phones a bottom bar.
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        setContent {
            MusicarrTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var signedIn by remember { mutableStateOf(MusicarrApp.instance.session.hasSession) }
                    if (!signedIn) {
                        LoginScreen(onLoggedIn = { signedIn = true })
                    } else {
                        CompositionLocalProvider(LocalPlayer provides connection) {
                            MainNav(isTv = isTv, onSignedOut = { signedIn = false })
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        playerConnection?.release()
        playerConnection = null
        super.onDestroy()
    }
}

@Composable
private fun MainNav(isTv: Boolean, onSignedOut: () -> Unit) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun navigateTop(route: String) {
        navController.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val signOut: () -> Unit = {
        scope.launch {
            MusicarrApp.instance.repository.logout()
            onSignedOut()
        }
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            NavHost(navController, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") {
                    HomeScreen(snackbar, onOpenAlbum = { navController.navigate("album/$it") }, onOpenArtist = { navController.navigate("artist/$it") })
                }
                composable("search") {
                    SearchScreen(snackbar, onOpenAlbum = { navController.navigate("album/$it") }, onOpenArtist = { navController.navigate("artist/$it") })
                }
                composable("library") {
                    LibraryScreen(snackbar, onOpenAlbum = { navController.navigate("album/$it") }, onOpenArtist = { navController.navigate("artist/$it") })
                }
                composable("playlists") {
                    PlaylistsScreen(onOpenPlaylist = { navController.navigate("playlist/$it") })
                }
                composable("downloads") { DownloadsScreen(snackbar) }
                composable("nowplaying") { NowPlayingScreen(snackbar) }
                composable("album/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                    AlbumScreen(id, snackbar, onOpenArtist = { navController.navigate("artist/$it") })
                }
                composable("artist/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                    ArtistScreen(id, snackbar, onOpenAlbum = { navController.navigate("album/$it") }, onOpenArtist = { navController.navigate("artist/$it") })
                }
                composable("playlist/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                    PlaylistDetailScreen(id, snackbar)
                }
            }
            MiniPlayer(onOpen = { navController.navigate("nowplaying") })
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!isTv) {
                NavigationBar {
                    TOP_DESTINATIONS.forEach { d ->
                        NavigationBarItem(
                            selected = currentRoute == d.route,
                            onClick = { navigateTop(d.route) },
                            icon = { Icon(d.icon, contentDescription = d.label) },
                            label = { Text(d.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (isTv) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                NavigationRail {
                    TOP_DESTINATIONS.forEach { d ->
                        NavigationRailItem(
                            selected = currentRoute == d.route,
                            onClick = { navigateTop(d.route) },
                            icon = { Icon(d.icon, contentDescription = d.label) },
                            label = { Text(d.label) },
                        )
                    }
                    NavigationRailItem(
                        selected = false,
                        onClick = signOut,
                        icon = { Icon(Icons.Default.Logout, contentDescription = "Sign out") },
                        label = { Text("Sign out") },
                    )
                }
                content(Modifier.fillMaxSize())
            }
        } else {
            content(Modifier.fillMaxSize().padding(padding))
        }
    }
}

/** Persistent bar above the navigation: what's playing now, tap to expand. */
@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val player = LocalPlayer.current
    val now by player.nowPlaying.collectAsState()
    if (now.trackId == 0L) return

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().dpadFocusable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(now.cover, 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(now.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = player::togglePlayPause) {
                Icon(
                    if (now.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (now.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = player::next, enabled = now.hasNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next")
            }
        }
    }
}
