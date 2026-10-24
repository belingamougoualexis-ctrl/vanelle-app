package com.vanelle.meshnet

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MeshNetUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun simulatorButtonRunsRealPacketCoreWithoutRadio() {
        composeRule.onNodeWithText("Lancer le test logiciel").assertExists().performClick()
        composeRule.onNode(hasText("SIMULATION LOGICIELLE", substring = true)).assertExists()
        composeRule.onNode(hasText("aucun appareil distant testé", substring = true)).assertExists()
    }

    @Test
    fun sendWithoutPeerShowsHonestConnectionState() {
        composeRule.onNodeWithText("Écrire un message…").assertExists().performTextInput("test local")
        composeRule.onNodeWithText("Envoyer sur le réseau local").performClick()
        composeRule.onNode(hasText("Aucun transport de messagerie connecté", substring = true)).assertExists()
    }
}
