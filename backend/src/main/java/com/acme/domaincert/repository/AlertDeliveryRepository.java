package com.acme.domaincert.repository;

import com.acme.domaincert.domain.AlertDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 通过唯一业务键避免同一收件人重复收到相同档位提醒。 */
public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {
    Optional<AlertDelivery> findByAssetTypeAndAssetIdAndAlertDaysAndExpirationDateAndRecipient(
            String assetType, Long assetId, int alertDays, LocalDate expirationDate, String recipient);
    List<AlertDelivery> findTop100ByOrderByIdDesc();
    List<AlertDelivery> findTop100ByStatusOrderByIdAsc(String status);
}
