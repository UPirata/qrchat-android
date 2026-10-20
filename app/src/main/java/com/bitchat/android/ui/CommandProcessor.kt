package com.bitchat.android.ui

import com.bitchat.android.mesh.MeshService
import com.bitchat.android.model.BitchatMessage
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles processing of IRC-style commands
 */
class CommandProcessor(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private val privateChatManager: PrivateChatManager,
    private val coroutineScope: CoroutineScope? = null
) {
    
    // Available commands list
    private val baseCommands = listOf(
        CommandSuggestion("/block", emptyList(), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_nickname_opt, "[nickname]"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_block, "block or list blocked peers")),
        CommandSuggestion("/channels", emptyList(), null, com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_channels, "show all discovered channels")),
        CommandSuggestion("/clear", emptyList(), null, com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_clear, "clear chat messages")),
        CommandSuggestion("/hug", emptyList(), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_nickname, "<nickname>"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_hug, "send someone a warm hug")),
        CommandSuggestion("/j", listOf("/join"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_channel, "<channel>"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_join, "join or create a channel")),
        CommandSuggestion("/m", listOf("/msg"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_msg, "<nickname> [message]"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_msg, "send private message")),
        CommandSuggestion("/pay", emptyList(), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_pay, "<token> [public]"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_pay, "send a Cashu ecash token")),
        CommandSuggestion("/slap", emptyList(), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_nickname, "<nickname>"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_slap, "slap someone with a trout")),
        CommandSuggestion("/unblock", emptyList(), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_nickname, "<nickname>"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_unblock, "unblock a peer")),
        CommandSuggestion("/w", emptyList(), null, com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_who, "see who's online"))
    )
    
    // MARK: - Command Processing
    
    fun processCommand(command: String, meshService: MeshService, myPeerID: String, onSendMessage: (String, List<String>, String?) -> Unit, viewModel: ChatViewModel? = null): Boolean {
        if (!command.startsWith("/")) return false
        
        val parts = command.split(" ")
        val cmd = parts.first().lowercase()
        when (cmd) {
            "/j", "/join" -> handleJoinCommand(parts, myPeerID)
            "/m", "/msg" -> handleMessageCommand(parts, meshService, viewModel)
            "/pay" -> handlePayCommand(command, meshService, myPeerID, onSendMessage, viewModel)
            "/w" -> handleWhoCommand(meshService, viewModel)
            "/clear" -> handleClearCommand()
            "/pass" -> handlePassCommand(parts, myPeerID)
            "/block" -> handleBlockCommand(parts, meshService)
            "/unblock" -> handleUnblockCommand(parts, meshService)
            "/hug" -> handleActionCommand(parts, "gives", "a warm hug 🫂", meshService, myPeerID, onSendMessage, viewModel)
            "/slap" -> handleActionCommand(parts, "slaps", "around a bit with a large trout 🐟", meshService, myPeerID, onSendMessage, viewModel)
            "/channels" -> handleChannelsCommand()
            else -> handleUnknownCommand(cmd)
        }
        
        return true
    }
    
    private fun handleJoinCommand(parts: List<String>, myPeerID: String) {
        if (parts.size > 1) {
            val channelName = parts[1]
            val channel = if (channelName.startsWith("#")) channelName else "#$channelName"
            val password = if (parts.size > 2) parts[2] else null
            val success = channelManager.joinChannel(channel, password, myPeerID)
            if (success) {
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_joined_channel, "joined channel %1\$s", channel),
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_usage_join, "usage: /join <channel>"),
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }
    
    private fun handleMessageCommand(parts: List<String>, meshService: MeshService, viewModel: ChatViewModel?) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            val peerID = getPeerIDForNickname(targetName, meshService)
            
            if (peerID != null) {
                val success = privateChatManager.startPrivateChat(peerID, meshService)
                
                if (success) {
                    if (parts.size > 2) {
                        val messageContent = parts.drop(2).joinToString(" ")
                        val recipientNickname = getPeerNickname(peerID, meshService)
                        sendPrivateMessage(
                            messageContent, 
                            peerID, 
                            recipientNickname,
                            state.getNicknameValue(),
                            getMyPeerID(meshService),
                            meshService,
                            viewModel
                        )
                    } else {
                        val systemMessage = BitchatMessage(
                            sender = "system",
                            content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_started_private_chat, "started private chat with %1\$s", targetName),
                            timestamp = Date(),
                            isRelay = false
                        )
                        messageManager.addMessage(systemMessage)
                    }
                }
            } else {
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_user_not_found_offline, "user '%1\$s' not found. they may be offline or using a different nickname.", targetName),
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_usage_msg, "usage: /msg <nickname> [message]"),
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }
    
    private fun handleWhoCommand(meshService: MeshService, viewModel: ChatViewModel? = null) {
        // Channel-aware who command (matches iOS behavior)
        val (peerList, contextDescription) = if (viewModel != null) {
            when (val selectedChannel = viewModel.selectedLocationChannel.value) {
                is com.bitchat.android.geohash.ChannelID.Mesh,
                null -> {
                    // Mesh channel: show Bluetooth-connected peers
                    val connectedPeers = state.getConnectedPeersValue()
                    val peerList = connectedPeers.joinToString(", ") { peerID ->
                        getPeerNickname(peerID, meshService)
                    }
                    Pair(peerList, com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_online_users, "online users"))
                }
                
                is com.bitchat.android.geohash.ChannelID.Location -> {
                    // Location channel: show geohash participants
                    val geohashPeople = viewModel.geohashPeople.value ?: emptyList()
                    val currentNickname = state.getNicknameValue()
                    
                    val participantList = geohashPeople.mapNotNull { person ->
                        val displayName = person.displayName
                        // Exclude self from list
                        if (displayName.startsWith("${currentNickname}#")) {
                            null
                        } else {
                            displayName
                        }
                    }.joinToString(", ")
                    
                    Pair(participantList, com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_participants_in, "participants in %1\$s", selectedChannel.channel.geohash))
                }
            }
        } else {
            // Fallback to mesh behavior
            val connectedPeers = state.getConnectedPeersValue()
            val peerList = connectedPeers.joinToString(", ") { peerID ->
                getPeerNickname(peerID, meshService)
            }
            Pair(peerList, com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_online_users, "online users"))
        }
        
        val systemMessage = BitchatMessage(
            sender = "system",
            content = if (peerList.isEmpty()) {
                com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_no_one_around, "no one else is around right now.")
            } else {
                "$contextDescription: $peerList"
            },
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }
    
    private fun handleClearCommand() {
        when {
            state.getSelectedPrivateChatPeerValue() != null -> {
                // Clear private chat
                val peerID = state.getSelectedPrivateChatPeerValue()!!
                messageManager.clearPrivateMessages(peerID)
                // `/clear` removes history but should not navigate away from the chat the
                // command was issued in. A later message will repopulate this conversation.
                state.setSelectedPrivateChatPeer(peerID)
            }
            state.getCurrentChannelValue() != null -> {
                // Clear channel messages
                val channel = state.getCurrentChannelValue()!!
                messageManager.clearChannelMessages(channel)
            }
            else -> {
                // Clear main messages
                messageManager.clearMessages()
            }
        }
    }

    private fun handlePassCommand(parts: List<String>, peerID: String) {
        val currentChannel = state.getCurrentChannelValue()

        if (currentChannel == null) {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_pass_need_channel, "you must be in a channel to set a password."),
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return
        }

        if (parts.size == 2){
            if(!channelManager.isChannelCreator(channel = currentChannel, peerID = peerID)){
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_pass_need_creator, "you must be the channel creator to set a password."),
                    timestamp = Date(),
                    isRelay = false
                )
                channelManager.addChannelMessage(currentChannel,systemMessage,null)
                return
            }
            val newPassword = parts[1]
            channelManager.setChannelPassword(currentChannel, newPassword)
            val systemMessage = BitchatMessage(
                sender = "system",
                content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_pass_changed, "password changed for channel %1\$s", currentChannel),
                timestamp = Date(),
                isRelay = false
            )
            channelManager.addChannelMessage(currentChannel,systemMessage,null)
        }
        else{
            val systemMessage = BitchatMessage(
                sender = "system",
                content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_usage_pass, "usage: /pass <password>"),
                timestamp = Date(),
                isRelay = false
            )
            channelManager.addChannelMessage(currentChannel,systemMessage,null)
        }
    }
    
    private fun handleBlockCommand(parts: List<String>, meshService: MeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            privateChatManager.blockPeerByNickname(targetName, meshService)
        } else {
            // List blocked users
            val blockedInfo = privateChatManager.listBlockedUsers()
            val systemMessage = BitchatMessage(
                sender = "system",
                content = blockedInfo,
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }
    
    private fun handleUnblockCommand(parts: List<String>, meshService: MeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            privateChatManager.unblockPeerByNickname(targetName, meshService)
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_usage_unblock, "usage: /unblock <nickname>"),
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }
    
    private fun handleActionCommand(
        parts: List<String>, 
        verb: String, 
        object_: String, 
        meshService: MeshService,
        myPeerID: String,
        onSendMessage: (String, List<String>, String?) -> Unit,
        viewModel: ChatViewModel?
    ) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            val actionMessage = "* ${state.getNicknameValue() ?: "someone"} $verb $targetName $object_ *"

            // If we're in a geohash location channel, don't add a local echo here.
            // GeohashViewModel.sendGeohashMessage() will add the local echo with proper metadata.
            val isInLocationChannel = state.selectedLocationChannel.value is com.bitchat.android.geohash.ChannelID.Location

            // Send as regular message
            if (state.getSelectedPrivateChatPeerValue() != null) {
                val peerID = state.getSelectedPrivateChatPeerValue()!!
                sendPrivateMessage(
                    actionMessage,
                    peerID,
                    getPeerNickname(peerID, meshService),
                    state.getNicknameValue(),
                    myPeerID,
                    meshService,
                    viewModel
                )
            } else if (isInLocationChannel) {
                // Let the transport layer add the echo; just send it out
                onSendMessage(actionMessage, emptyList(), null)
            } else {
                val message = BitchatMessage(
                    sender = state.getNicknameValue() ?: myPeerID,
                    content = actionMessage,
                    timestamp = Date(),
                    isRelay = false,
                    senderPeerID = myPeerID,
                    channel = state.getCurrentChannelValue()
                )
                
                if (state.getCurrentChannelValue() != null) {
                    channelManager.addChannelMessage(state.getCurrentChannelValue()!!, message, myPeerID)
                    onSendMessage(actionMessage, emptyList(), state.getCurrentChannelValue())
                } else {
                    messageManager.addMessage(message)
                    onSendMessage(actionMessage, emptyList(), null)
                }
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_usage_action, "usage: /%1\$s <nickname>", parts[0].removePrefix("/")),
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }
    
    private fun handleChannelsCommand() {
        val allChannels = channelManager.getJoinedChannelsList()
        val channelList = if (allChannels.isEmpty()) {
            com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_no_channels, "no channels joined")
        } else {
            com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_joined_channels, "joined channels: %1\$s", allChannels.joinToString(", "))
        }
        
        val systemMessage = BitchatMessage(
            sender = "system",
            content = channelList,
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handlePayCommand(
        command: String,
        meshService: MeshService,
        myPeerID: String,
        onSendMessage: (String, List<String>, String?) -> Unit,
        viewModel: ChatViewModel?
    ) {
        val args = command.trim().split(Regex("\\s+")).drop(1)
        if (args.isEmpty()) {
            addSystemMessage(com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_usage_pay, "usage: /pay <cashu token> [public] — Cashu tokens are bearer instruments"))
            return
        }

        val publicConfirmed = args.lastOrNull()?.equals("public", ignoreCase = true) == true
        val rawToken = if (publicConfirmed) args.dropLast(1).joinToString(" ") else args.joinToString(" ")
        val token = CashuTokenDecoder.bareToken(rawToken)
        val info = token?.let { CashuTokenDecoder.decode(it, strict = true) }
        if (token == null || info == null) {
            addSystemMessage(com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_invalid_cashu, "invalid cashu token — not sending it"))
            return
        }

        val selectedPeer = state.getSelectedPrivateChatPeerValue()
        if (selectedPeer != null) {
            privateChatManager.sendPrivateMessage(
                token,
                selectedPeer,
                getPeerNickname(selectedPeer, meshService),
                state.getNicknameValue(),
                myPeerID
            ) { content, peerID, recipientNickname, messageId ->
                sendPrivateMessageVia(meshService, content, peerID, recipientNickname, messageId, viewModel)
            }
        } else {
            if (!publicConfirmed) {
                addSystemMessage(
                    "Cashu tokens are bearer instruments. Anyone here can redeem this token. " +
                        "Confirm with: /pay <token> public"
                )
                return
            }
            val isLocationChannel =
                state.selectedLocationChannel.value is com.bitchat.android.geohash.ChannelID.Location
            if (!isLocationChannel) {
                val message = BitchatMessage(
                    sender = state.getNicknameValue() ?: myPeerID,
                    content = token,
                    timestamp = Date(),
                    isRelay = false,
                    senderPeerID = myPeerID,
                    channel = state.getCurrentChannelValue()
                )
                val channel = state.getCurrentChannelValue()
                if (channel != null) channelManager.addChannelMessage(channel, message, myPeerID)
                else messageManager.addMessage(message)
            }
            onSendMessage(token, emptyList(), state.getCurrentChannelValue())
        }

        addSystemMessage(
            "sent ${info.displayAmount ?: "Cashu token"} — bearer token; first redeemer wins"
        )
    }

    private fun addSystemMessage(content: String) {
        val message = BitchatMessage(
            sender = "system",
            content = content,
            timestamp = Date(),
            isRelay = false
        )
        val selectedPeer = state.getSelectedPrivateChatPeerValue()
        val selectedLocationChannel = state.selectedLocationChannel.value
        val channel = state.getCurrentChannelValue()
        when {
            selectedPeer != null -> {
                messageManager.addPrivateMessageNoUnread(selectedPeer, message.copy(isPrivate = true))
            }
            selectedLocationChannel is com.bitchat.android.geohash.ChannelID.Location -> {
                messageManager.addChannelMessage(
                    "geo:${selectedLocationChannel.channel.geohash}",
                    message
                )
            }
            channel != null -> channelManager.addChannelMessage(channel, message, null)
            else -> messageManager.addMessage(message)
        }
    }
    
    private fun handleUnknownCommand(cmd: String) {
        val systemMessage = BitchatMessage(
            sender = "system",
            content = com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.sys_unknown_command, "unknown command: %1\$s. type / to see available commands.", cmd),
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }
    
    /**
     * Dismiss the command and mention popups. The composer clears its field in code
     * after a send, which does not run onValueChange, so the popups need an explicit
     * clear.
     */
    fun clearSuggestions() {
        state.setShowCommandSuggestions(false)
        state.setCommandSuggestions(emptyList())
        state.setShowMentionSuggestions(false)
        state.setMentionSuggestions(emptyList())
    }

    // MARK: - Command Autocomplete

    fun updateCommandSuggestions(input: String) {
        if (!input.startsWith("/")) {
            state.setShowCommandSuggestions(false)
            state.setCommandSuggestions(emptyList())
            return
        }
        
        // Get all available commands based on context
        val allCommands = getAllAvailableCommands()
        
        // Filter commands based on input
        val filteredCommands = filterCommands(allCommands, input.lowercase())
        
        if (filteredCommands.isNotEmpty()) {
            state.setCommandSuggestions(filteredCommands)
            state.setShowCommandSuggestions(true)
        } else {
            state.setShowCommandSuggestions(false)
            state.setCommandSuggestions(emptyList())
        }
    }
    
    private fun getAllAvailableCommands(): List<CommandSuggestion> {
        // Add channel-specific commands if in a channel
        val channelCommands = if (state.getCurrentChannelValue() != null) {
            listOf(
                CommandSuggestion("/pass", emptyList(), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_password, "[password]"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_pass, "change channel password")),
                CommandSuggestion("/save", emptyList(), null, com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_save, "save channel messages locally")),
                CommandSuggestion("/transfer", emptyList(), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_arg_nickname, "<nickname>"), com.bitchat.android.util.AppStrings.get(com.bitchat.android.R.string.cmd_desc_transfer, "transfer channel ownership"))
            )
        } else {
            emptyList()
        }
        
        val isPublicGeohash =
            state.getSelectedPrivateChatPeerValue() == null &&
                state.selectedLocationChannel.value is com.bitchat.android.geohash.ChannelID.Location
        return (baseCommands + channelCommands).filterNot {
            isPublicGeohash && it.command == "/pay"
        }
    }
    
    private fun filterCommands(commands: List<CommandSuggestion>, input: String): List<CommandSuggestion> {
        return commands.filter { command ->
            // Check primary command
            command.command.startsWith(input) ||
            // Check aliases
            command.aliases.any { it.startsWith(input) }
        }.sortedBy { it.command }
    }
    
    fun selectCommandSuggestion(suggestion: CommandSuggestion): String {
        state.setShowCommandSuggestions(false)
        state.setCommandSuggestions(emptyList())
        return "${suggestion.command} "
    }
    
    // MARK: - Mention Autocomplete
    
    fun updateMentionSuggestions(input: String, meshService: MeshService, viewModel: ChatViewModel? = null) {
        // Check if input contains @ and we're at the end of a word or at the end of input
        val atIndex = input.lastIndexOf('@')
        if (atIndex == -1) {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
            return
        }
        
        // Get the text after the @ symbol
        val textAfterAt = input.substring(atIndex + 1)
        
        // If there's a space after @, don't show suggestions
        if (textAfterAt.contains(' ')) {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
            return
        }
        
        // Get peer candidates based on active channel (matches iOS logic exactly)
        val peerCandidates: List<String> = if (viewModel != null) {
            when (val selectedChannel = viewModel.selectedLocationChannel.value) {
                is com.bitchat.android.geohash.ChannelID.Mesh,
                null -> {
                    // Mesh channel: use Bluetooth mesh peer nicknames
                    val peerNicknames = meshService.getPeerNicknames()
                    peerNicknames.values.filter { it != peerNicknames[meshService.myPeerID] }
                }
                
                is com.bitchat.android.geohash.ChannelID.Location -> {
                    // Location channel: use geohash participants with collision-resistant suffixes
                    val geohashPeople = viewModel.geohashPeople.value
                    val currentNickname = state.getNicknameValue()
                    val duplicateNames = duplicateGeohashBaseNames(geohashPeople)
                    
                    geohashPeople.mapNotNull { person ->
                        val baseName = splitSuffix(person.displayName).first
                        val hasNicknameCollision =
                            baseName.lowercase(Locale.ROOT) in duplicateNames
                        val displayName = disambiguatedGeohashDisplayName(person, duplicateNames)
                        // A unique local nickname can be excluded directly. If it collides, the
                        // nickname alone cannot identify which row is self, so keep the suffixed
                        // rows rather than accidentally hiding the other user.
                        if (
                            !hasNicknameCollision &&
                            baseName.equals(currentNickname, ignoreCase = true)
                        ) {
                            null
                        } else {
                            displayName
                        }
                    }
                }
            }
        } else {
            // Fallback to mesh peers if no viewModel available
            val peerNicknames = meshService.getPeerNicknames()
            peerNicknames.values.filter { it != peerNicknames[meshService.myPeerID] }
        }
        
        val filteredNicknames = filterMentionCandidates(peerCandidates, textAfterAt)
        
        if (filteredNicknames.isNotEmpty()) {
            state.setMentionSuggestions(filteredNicknames)
            state.setShowMentionSuggestions(true)
        } else {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
        }
    }
    
    fun selectMentionSuggestion(nickname: String, currentText: String): String {
        state.setShowMentionSuggestions(false)
        state.setMentionSuggestions(emptyList())
        
        // Find the last @ symbol position
        val atIndex = currentText.lastIndexOf('@')
        if (atIndex == -1) {
            return "$currentText@$nickname "
        }
        
        // Replace the text from the @ symbol to the end with the mention
        val textBeforeAt = currentText.substring(0, atIndex)
        return "$textBeforeAt@$nickname "
    }
    
    // MARK: - Utility Functions
    
    private fun getPeerIDForNickname(nickname: String, meshService: MeshService): String? {
        return meshService.getPeerNicknames().entries.find { it.value == nickname }?.key
    }
    
    private fun getPeerNickname(peerID: String, meshService: MeshService): String {
        return meshService.getPeerNicknames()[peerID]
            ?: peerID
    }
    
    private fun getMyPeerID(meshService: MeshService): String {
        return meshService.myPeerID
    }

    private fun sendPrivateMessage(
        content: String,
        peerID: String,
        recipientNickname: String?,
        senderNickname: String?,
        myPeerID: String,
        meshService: MeshService,
        viewModel: ChatViewModel?
    ) {
        val send: (String, String, String, String) -> Unit =
            { messageContent, peerIdParam, recipientNicknameParam, messageId ->
                sendPrivateMessageVia(
                    meshService,
                    messageContent,
                    peerIdParam,
                    recipientNicknameParam,
                    messageId,
                    viewModel
                )
            }
        val scope = coroutineScope
        if (scope == null) {
            privateChatManager.sendPrivateMessage(
                content,
                peerID,
                recipientNickname,
                senderNickname,
                myPeerID,
                send
            )
        } else {
            scope.launch {
                privateChatManager.sendPrivateMessageDurably(
                    content,
                    peerID,
                    recipientNickname,
                    senderNickname,
                    myPeerID,
                    send
                )
            }
        }
    }

    private fun sendPrivateMessageVia(
        meshService: MeshService,
        content: String,
        peerID: String,
        recipientNickname: String,
        messageId: String,
        viewModel: ChatViewModel?
    ) {
        if (viewModel != null) {
            com.bitchat.android.services.MessageRouter
                .getInstance(viewModel.getApplication(), meshService)
                .sendPrivate(content, peerID, recipientNickname, messageId)
        } else {
            meshService.sendPrivateMessage(content, peerID, recipientNickname, messageId)
        }
    }
}

/**
 * Keep mention autocomplete useful in crowded channels: a bare `anon` identity has not announced
 * a username and is not actionable. Names such as `anon1234` are announced usernames and remain
 * valid mention targets.
 */
internal fun filterMentionCandidates(
    candidates: List<String>,
    query: String
): List<String> {
    return candidates.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot(::isUnannouncedNickname)
        .filter { nickname -> nickname.startsWith(query, ignoreCase = true) }
        .distinctBy { nickname -> nickname.lowercase(Locale.ROOT) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .toList()
}
