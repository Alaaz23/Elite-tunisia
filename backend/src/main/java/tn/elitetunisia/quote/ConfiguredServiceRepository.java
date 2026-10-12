package tn.elitetunisia.quote;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ConfiguredServiceRepository extends JpaRepository<ConfiguredServiceEntity, UUID> {
    List<ConfiguredServiceEntity> findByActiveTrueAndCurrencyOrderByNameAsc(String currency);
}