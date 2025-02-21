package com.alibou.websocket.chat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.alibou.websocket.chatroom.ChatRoomService;
import com.alibou.websocket.user.Status;
import com.alibou.websocket.user.User;
import com.alibou.websocket.user.UserRepository;

import lombok.RequiredArgsConstructor; 





@Service
@RequiredArgsConstructor
public class ChatMessageService {


    private final ChatMessageRepository repository;
    private final ChatRoomService chatRoomService;
    private final UserRepository userRepository;
    
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String AI_API_URL = "http://172.31.2.2:8181/query-chat";

    public void processMessage(ChatMessage chatMessage) {
    
        ChatMessage savedMsg = save(chatMessage);
        messagingTemplate.convertAndSendToUser(
                chatMessage.getRecipientId(), "/queue/messages",
                new ChatNotification(
                        savedMsg.getId(),
                        savedMsg.getSenderId(),
                        savedMsg.getRecipientId(),
                        savedMsg.getContent()
                )
        );

        // Handle AI response logic
        handleAIResponseIfNeeded(chatMessage);
    }

    private void handleAIResponseIfNeeded(ChatMessage chatMessage) {
        boolean isRecipientManager = checkIfRecipientIsManager(chatMessage.getRecipientId());
        boolean isManagerOnline = checkIfManagerIsOnline(chatMessage.getRecipientId());
    
        // ✅ First, store the original user message (Already handled in `processMessage`)
        if (!isManagerOnline && isRecipientManager) {
            
              
            String originalSenderId = chatMessage.getSenderId();
            String originalRecipientId = chatMessage.getRecipientId(); 

            // ✅ Get AI response
            String aiResponse = getAIResponse(chatMessage.getContent());
    
        ChatMessage aiMessage = new ChatMessage();
        aiMessage.setSenderId("AI");
        aiMessage.setRecipientId(chatMessage.getSenderId()); // AI replies to the sender
        aiMessage.setContent(getAIResponse(chatMessage.getContent())); // ✅ Directly assign the response
        aiMessage.setChatId(chatMessage.getChatId()); // Maintain the same chat ID

        
        ChatMessage savedAiMsg = saveAI(aiMessage);
        ChatMessage savedAiMsg2=new ChatMessage();
        savedAiMsg2.setChatId(savedAiMsg.getChatId());
        savedAiMsg2.setSenderId(originalRecipientId);
        savedAiMsg2.setRecipientId(originalSenderId);
        savedAiMsg.setContent(aiResponse);


         messagingTemplate.convertAndSendToUser(
                  originalSenderId, "/queue/messages", savedAiMsg2
               );

            // messagingTemplate.convertAndSendToUser(
            //         originalSenderId, "/queue/messages",
            //         new ChatNotification(
            //                 savedAiMsg.getId(),
            //                 originalRecipientId,
            //                 originalSenderId,     
            //                 savedAiMsg.getContent()
            //         )
            // );
        }
    }
    

    private boolean checkIfRecipientIsManager(String recipientId) {
        Optional<User> userOptional = userRepository.findById(recipientId);
        return userOptional.isPresent() && "MANAGER".equalsIgnoreCase(userOptional.get().getRole());
    }

    private boolean checkIfManagerIsOnline(String recipientId) {
        Optional<User> userOptional = userRepository.findById(recipientId);
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            return "MANAGER".equalsIgnoreCase(user.getRole()) && user.getStatus() == Status.ONLINE;
        }
        
        return false; // Return false if user is not found
    }
    

    private String getAIResponse(String userQuery) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        String requestBody = String.format("{\"userQuery\":\"%s\"}", userQuery);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<List> response = restTemplate.exchange(AI_API_URL, HttpMethod.POST, entity, List.class);
        List<Map<String, Object>> responseBody = response.getBody();

        if (responseBody != null && !responseBody.isEmpty()) {
            Map<String, Object> output = (Map<String, Object>) responseBody.get(0).get("output");
            return (String) output.get("content");
        }
        return "I'm sorry, I couldn't process that.";
    }

    public ChatMessage save(ChatMessage chatMessage) {
        var chatId = chatRoomService
                .getChatRoomId(chatMessage.getSenderId(), chatMessage.getRecipientId(), true)
                .orElseThrow(); // Ensure valid chat room
        chatMessage.setChatId(chatId);
        repository.save(chatMessage);
        return chatMessage;
    }

    public ChatMessage saveAI(ChatMessage chatMessage) {
        chatMessage.setTimestamp(new Date());
        repository.save(chatMessage);
        return chatMessage;
    }

   

    public List<ChatMessage> findChatMessages(String senderId, String recipientId) {
        var chatId = chatRoomService.getChatRoomId(senderId, recipientId, false);
        return chatId.map(repository::findByChatId).orElse(new ArrayList<>());
    }
}
