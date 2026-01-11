package com.alertops.flow.repository;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.alertops.flow.model.Node;

@Repository
public interface NodeRepository extends JpaRepository<Node, UUID> {
    Node findTopByFlowIdOrderByPositionDesc(UUID flowId);

    List<Node> findAllByFlowIdOrderByPositionAsc(UUID flowId);

    Node findTopByFlowIdOrderByPositionAsc(UUID flowId);

    @Query("SELECT n FROM Node n WHERE n.flowId = :flowId AND n.position > :position ORDER BY n.position ASC limit 1")
    Node findNextNode(UUID flowId, BigInteger position);

}