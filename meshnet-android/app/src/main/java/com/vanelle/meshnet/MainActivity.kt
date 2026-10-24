package com.vanelle.meshnet

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.UUID

private val Ink = Color(0xFF0A1020)
private val Panel = Color(0xFF131D31)
private val Cyan = Color(0xFF65E6D4)
private val Violet = Color(0xFF9A8CFF)
private val Muted = Color(0xFFA7B4C8)

class MainActivity : ComponentActivity() {
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var receiverRegistered = false
    private var peers by mutableStateOf<List<WifiP2pDevice>>(emptyList())
    private var status by mutableStateOf("Prêt à vérifier les capacités de l’appareil")
    private var wifiDirectState by mutableStateOf("Non vérifié")
    private var connectionState by mutableStateOf("Aucune connexion")
    private var isDiscovering by mutableStateOf(false)
    private var demoResult by mutableStateOf("Aucun test local lancé")
    private var permissionGranted by mutableStateOf(false)
    private val transport = MeshTransport()
    private var chatMessages by mutableStateOf<List<String>>(emptyList())
    private var draft by mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionGranted = requiredPermissions().all { p ->
            grants[p] == true || ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        }
        status = if (permissionGranted) "Autorisations accordées" else "Autorisation requise pour la découverte Wi-Fi Direct"
        if (permissionGranted) discoverPeers()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(this, mainLooper, null)
        permissionGranted = requiredPermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        status = if (manager == null) "Wi-Fi Direct indisponible sur ce système" else "Moteur VAMP initialisé · radio non encore testée"
        registerP2pReceiver()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Violet, background = Ink, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
                MeshNetScreen(status, wifiDirectState, connectionState, peers, isDiscovering, demoResult, permissionGranted, chatMessages, draft,
                    { requestRequiredPermissions() }, { if (permissionGranted) discoverPeers() else requestRequiredPermissions() },
                    { requestPeers() }, { connectTo(it) }, { disconnect() }, { runLocalSimulation() },
                    { draft = it }, {
                        val message = draft.trim()
                        if (message.isNotEmpty()) {
                            if (transport.send(message)) {
                                chatMessages = (chatMessages + "Moi : $message").takeLast(100)
                                draft = ""
                            } else status = "Aucun transport de messagerie connecté. Connecte-toi à un appareil compatible."
                        }
                    })
            }
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun requestRequiredPermissions() {
        val missing = requiredPermissions().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            permissionGranted = true
            status = "Autorisations déjà accordées"
            discoverPeers()
        } else permissionLauncher.launch(missing.toTypedArray())
    }

    @Suppress("DEPRECATION")
    private fun registerP2pReceiver() {
        if (receiverRegistered) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        wifiDirectState = if (enabled) "Disponible selon Android" else "Désactivé ou indisponible"
                        if (!enabled) status = "Active le Wi-Fi dans les paramètres système"
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        @Suppress("DEPRECATION")
                        val network = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        connectionState = if (network?.isConnected == true) "Connecté · liaison système établie" else "Aucune connexion"
                        if (network?.isConnected == true) manager?.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                            connectionState = if (info.groupFormed) "Groupe P2P établi" else "Connexion en négociation"
                            if (info.groupFormed) {
                                if (info.isGroupOwner) {
                                    transport.startGroupOwner(
                                        { message -> runOnUiThread { chatMessages = (chatMessages + "Pair : $message").takeLast(100) } },
                                        { value -> runOnUiThread { connectionState = value } }
                                    )
                                } else {
                                    info.groupOwnerAddress?.hostAddress?.let { host ->
                                        transport.connect(
                                            host,
                                            { message -> runOnUiThread { chatMessages = (chatMessages + "Pair : $message").takeLast(100) } },
                                            { value -> runOnUiThread { connectionState = value } }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        val p2pReceiver = receiver ?: return
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(p2pReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(p2pReceiver, filter)
        receiverRegistered = true
    }

    @Suppress("MissingPermission")
    private fun discoverPeers() {
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) { status = "API Wi-Fi Direct non disponible"; return }
        if (!permissionGranted) { requestRequiredPermissions(); return }
        isDiscovering = true
        status = "Recherche des appareils à proximité…"
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { status = "Recherche lancée · attends les résultats du système"; isDiscovering = false }
            override fun onFailure(reason: Int) { status = "Échec de la recherche (code système $reason). Vérifie le Wi-Fi et les autorisations."; isDiscovering = false }
        })
    }

    @Suppress("MissingPermission")
    private fun requestPeers() {
        if (!permissionGranted || manager == null || channel == null) return
        manager?.requestPeers(channel) { list: WifiP2pDeviceList ->
            peers = list.deviceList.toList()
            status = if (peers.isEmpty()) "Aucun voisin détecté pour le moment" else "${peers.size} appareil(s) découvert(s) par Android"
        }
    }

    @Suppress("MissingPermission")
    private fun connectTo(device: WifiP2pDevice) {
        if (!permissionGranted) { requestRequiredPermissions(); return }
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        status = "Demande de connexion à ${device.deviceName}…"
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { status = "Demande envoyée · la connexion reste à confirmer par Android" }
            override fun onFailure(reason: Int) { status = "Connexion refusée par le système (code $reason)" }
        })
    }

    @Suppress("MissingPermission")
    private fun disconnect() {
        transport.close()
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { connectionState = "Aucune connexion"; status = "Groupe P2P fermé" }
            override fun onFailure(reason: Int) { status = "Impossible de fermer le groupe (code $reason)" }
        })
    }

    private fun runLocalSimulation() {
        val original = VampPacket.create(source = "A", destination = "C", payload = "hello Vanelle", ttl = 4)
        val encoded = original.encode()
        val decoded = VampPacket.decode(encoded)
        val forwarded = decoded?.forwarded()
        val cache = PacketDeduplicator()
        val firstAccepted = cache.firstTime(original.id)
        val duplicateRejected = !cache.firstTime(original.id)
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        val passed = decoded?.payload == "hello Vanelle" && forwarded?.ttl == 3 && firstAccepted && duplicateRejected
        demoResult = "SIMULATION LOGICIELLE · aller-retour paquet ${if (decoded != null) "OK" else "ÉCHEC"} · TTL après relais ${forwarded?.ttl ?: "expiré"} · doublon ${if (duplicateRejected) "bloqué" else "accepté"} · SHA-256 $digest. Aucune radio utilisée."
        status = if (passed) "Tests logiciels VAMP réussis · aucun appareil distant testé" else "Échec du test logiciel VAMP"
    }

    override fun onResume() {
        super.onResume()
        permissionGranted = requiredPermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
            receiverRegistered = false
        }
        transport.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun MeshNetScreen(
    status: String, wifiDirectState: String, connectionState: String, peers: List<WifiP2pDevice>,
    isDiscovering: Boolean, demoResult: String, permissionGranted: Boolean, chatMessages: List<String>, draft: String,
    onPermissions: () -> Unit, onScan: () -> Unit, onRefresh: () -> Unit,
    onConnect: (WifiP2pDevice) -> Unit, onDisconnect: () -> Unit, onSimulation: () -> Unit,
    onDraftChange: (String) -> Unit, onSend: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A31), Ink, Color(0xFF090D18))))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).background(Brush.linearGradient(listOf(Cyan, Violet)), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Text("V", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("VANELLE", fontSize = 12.sp, letterSpacing = 3.sp, color = Cyan, fontWeight = FontWeight.Bold)
                        Text("MeshNet", fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    Box(Modifier.background(Color(0xFF19352F), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp)) {
                        Text("BETA", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Un réseau. Sans dépendance au cloud.", color = Color.White, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
                    Text("Communication locale entre appareils compatibles. Aucune portée longue distance n’est promise.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("RADIO", wifiDirectState, Modifier.weight(1f))
                    MetricCard("LIEN", connectionState, Modifier.weight(1f))
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("État du réseau", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Box(Modifier.size(8.dp).background(if (permissionGranted) Cyan else Violet, CircleShape))
                        }
                        Text(status, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                        if (!permissionGranted) {
                            Button(onClick = onPermissions, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) {
                                Text("Autoriser la découverte à proximité", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(onClick = onScan, enabled = !isDiscovering, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) {
                                Text(if (isDiscovering) "Recherche en cours…" else "Rechercher les appareils")
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Appareils proches", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Text("Actualiser", color = Cyan) }
                }
                if (peers.isEmpty()) {
                    Box(Modifier.fillMaxWidth().background(Color(0x331D2940), RoundedCornerShape(18.dp)).padding(18.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⌁", color = Violet, fontSize = 38.sp)
                            Text("Aucun appareil affiché", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Lance une recherche. Les résultats dépendent du Wi-Fi Direct et des appareils voisins.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        peers.forEach { peer ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onConnect(peer) }) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(38.dp).background(Color(0xFF26314C), CircleShape), contentAlignment = Alignment.Center) { Text("↗", color = Cyan, fontSize = 20.sp) }
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(peer.deviceName.ifBlank { "Appareil sans nom" }, color = Color.White, fontWeight = FontWeight.Medium)
                                        Text(peerStatus(peer.status), color = Muted, fontSize = 11.sp)
                                    }
                                    Text("Connecter", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Messages locaux", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("PAIR À PAIR", color = Violet, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                        }
                        if (chatMessages.isEmpty()) Text("Connecte deux appareils compatibles pour échanger des messages sur le réseau local.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        chatMessages.takeLast(4).forEach { line -> Text(line, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp) }
                        OutlinedTextField(
                            value = draft, onValueChange = onDraftChange, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Écrire un message…", color = Muted) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Cyan, unfocusedBorderColor = Muted, cursorColor = Cyan
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() })
                        )
                        Button(onClick = onSend, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) {
                            Text("Envoyer sur le réseau local", fontWeight = FontWeight.Bold)
                        }
                        Text("Le transport actuel est local et non chiffré. N’envoie pas de données sensibles.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17172C)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Laboratoire VAMP", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("SIMULÉ", color = Violet, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Teste localement la création d’un paquet, son empreinte et la déduplication.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        OutlinedButton(onClick = onSimulation, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                            Text("Lancer le test logiciel")
                        }
                        if (demoResult != "Aucun test local lancé") Text(demoResult, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                        TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.End)) { Text("Déconnecter le groupe", color = Color(0xFFFFA6B3), fontSize = 12.sp) }
                    }
                }
                Text("Les résultats simulés ne sont pas des mesures radio.", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF17243A)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
        }
    }
}

private fun peerStatus(status: Int): String = when (status) {
    WifiP2pDevice.AVAILABLE -> "Disponible"
    WifiP2pDevice.INVITED -> "Invitation envoyée"
    WifiP2pDevice.CONNECTED -> "Connecté"
    WifiP2pDevice.FAILED -> "Échec précédent"
    WifiP2pDevice.UNAVAILABLE -> "Indisponible"
    else -> "État inconnu"
}
