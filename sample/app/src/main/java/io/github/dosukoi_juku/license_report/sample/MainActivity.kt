@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.dosukoi_juku.license_report.sample

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph
import androidx.navigation.NavHost
import androidx.navigation.Navigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.google.android.gms.oss.licenses.OssLicensesActivity
import io.github.dosukoi_juku.license_report.sample.ui.theme.SampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.buffer
import okio.source

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SampleTheme {
                Nav()
            }
        }
        assets.open("dependencies.json").use {
            val dependencies = it.readBytes().decodeToString()
            println(dependencies)
        }

    }
}

@Composable
fun Nav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Top) {
        composable<Top> {
            TopScreen(
                onOssLicensesPluginClick = {
                    navController.navigate(route = OssLicensesPlugin)
                },
                onLicenseeClick = {
                    navController.navigate(route = Licensee)
                },
                onLicenseReportClick = {
                    navController.navigate(LicenseReport)
                }
            )
        }
        composable<OssLicensesPlugin> {
            OssLicensesPluginScreen(
                onLibraryClick = {
                    navController.navigate(License(offset = it.offset, length = it.length))
                },
                onNavigationClick =  {
                    navController.popBackStack()
                }
            )
        }
        composable<License> { backStackEntry ->
            val license: License = backStackEntry.toRoute()
            LicenseScreen(
                offset = license.offset,
                length = license.length,
                onNavigationClick = {
                    navController.popBackStack()
                }
            )
        }
        composable<Licensee> { }
        composable<LicenseReport> { }
    }
}

@Serializable
data object Top

@Composable
fun TopScreen(
    onOssLicensesPluginClick: () -> Unit,
    onLicenseeClick: () -> Unit,
    onLicenseReportClick: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElevatedButton(onClick = onOssLicensesPluginClick) {
                Text(text = "OSS License Plugin")
            }
            ElevatedButton(onClick = onLicenseeClick) {
                Text(text = "Licensee")
            }
            ElevatedButton(onClick = onLicenseReportClick) {
                Text(text = "License Report")
            }
        }
    }
}

@Serializable
data object OssLicensesPlugin

@Composable
fun OssLicensesPluginScreen(
    onLibraryClick: (Library) -> Unit,
    onNavigationClick: () -> Unit
) {
    val (isLoading, setLoading) = remember { mutableStateOf(false) }
    val (libraries, setLibraries) = remember { mutableStateOf<List<Library>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            setLoading(true)
            val libraries = loadLibraries(context = context)
            setLibraries(libraries)
            setLoading(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "OSS License Plugin")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Text(text = "Loading...")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(libraries) { library ->
                    LibraryItem(
                        library = library,
                        onLibraryClick = onLibraryClick
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryItem(library: Library, onLibraryClick: (Library) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = { onLibraryClick(library) }),
        horizontalAlignment = Alignment.Start
    ) {
        Text(modifier = Modifier.padding(16.dp), text = library.name)
        HorizontalDivider()
    }
}

suspend fun loadLibraries(context: Context): List<Library> {
    return withContext(Dispatchers.IO) {
        context.resources
            .openRawResource(R.raw.third_party_license_metadata)
            .source()
            .buffer()
            .use {
                val libraries = mutableListOf<Library>()
                while (true) {
                    val line = it.readUtf8Line() ?: break
                    val (position, name) = line.split(' ', limit = 2)
                    val (offset, length) = position.split(':').map { it.toInt() }
                    libraries.add(Library(name, offset, length))
                }
                libraries.toList()
            }
            .sortedBy { it.name }
    }
}

@Serializable
data class Library(
    val name: String,
    val offset: Int,
    val length: Int,
)

@Serializable
data class License(
    val offset: Int,
    val length: Int,
)

@Composable
fun LicenseScreen(offset: Int, length: Int, onNavigationClick: () -> Unit) {
    val (license, setLicense) = remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        setLicense(loadLicense(context = context, offset = offset, length = length))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "ライセンス") },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = license)
        }
    }
}

suspend fun loadLicense(context: Context, offset: Int, length: Int): String {
    return withContext(Dispatchers.IO) {
        val license: String
        context.resources
            .openRawResource(R.raw.third_party_licenses)
            .source()
            .buffer()
            .use {
                it.skip(offset.toLong())
                license = it.readUtf8(length.toLong())
            }
        license
    }
}


@Serializable
data object Licensee

@Serializable
data object LicenseReport

