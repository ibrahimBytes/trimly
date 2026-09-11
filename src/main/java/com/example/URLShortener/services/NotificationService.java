package com.example.URLShortener.services;
import com.example.URLShortener.models.*;
import com.example.URLShortener.repository.NotificationRepository;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class NotificationService {
 private final NotificationRepository repository;
 @Transactional public void create(User user,String type,String title,String message,String linkPath){repository.save(Notification.builder().user(user).type(type).title(title).message(message).linkPath(linkPath).createdAt(LocalDateTime.now()).build());}
 @Transactional(readOnly=true) public List<Notification> list(User user){return repository.findTop50ByUserOrderByCreatedAtDesc(user);}
 @Transactional public boolean read(User user,Long id){return repository.findByIdAndUser(id,user).map(n->{if(n.getReadAt()==null)n.setReadAt(LocalDateTime.now());return true;}).orElse(false);}
 @Transactional public void readAll(User user){repository.findTop50ByUserOrderByCreatedAtDesc(user).forEach(n->{if(n.getReadAt()==null)n.setReadAt(LocalDateTime.now());});}
 @Transactional public boolean dismiss(User user,Long id){return repository.findByIdAndUser(id,user).map(n->{repository.delete(n);return true;}).orElse(false);}
 @Transactional(readOnly=true) public long unread(User user){return repository.countByUserAndReadAtIsNull(user);}
 @Transactional public void deleteFor(User user){repository.deleteByUser(user);}
}
