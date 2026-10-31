package tn.elitetunisia.quote;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface ConfiguredServiceRepository extends JpaRepository<ConfiguredServiceEntity, UUID> {
    List<ConfiguredServiceEntity> findByActiveTrueAndCurrencyOrderByNameAsc(String currency);

    @Query("select s from ConfiguredServiceEntity s where :search = '' or lower(s.name) like lower(concat('%', :search, '%'))")
    Page<ConfiguredServiceEntity> search(String search, Pageable pageable);

    @Query("select s from ConfiguredServiceEntity s where s.active = :active and (:search = '' or lower(s.name) like lower(concat('%', :search, '%')))")
    Page<ConfiguredServiceEntity> searchByActive(String search, boolean active, Pageable pageable);

    @Query("select count(s) from ConfiguredServiceEntity s where s.active = true and s.currency = :currency and lower(s.name) = lower(:name) and s.id <> :excludedId")
    long countActiveNamed(String name, String currency, UUID excludedId);
}