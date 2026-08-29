package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.RequestNotification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IRequestNotificationRepo extends JpaRepository<RequestNotification, Long> {

    List<RequestNotification> findByRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);
}