package com.jdres.repository;

import com.jdres.model.MatchResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchResultRepository extends MongoRepository<MatchResult, String> {
    List<MatchResult> findByJdIdOrderByFinalScoreDesc(String jdId);

    List<MatchResult> findByResumeIdOrderByFinalScoreDesc(String resumeId);

    void deleteByJdId(String jdId);

    void deleteByResumeId(String resumeId);

    java.util.Optional<MatchResult> findByJdIdAndResumeId(String jdId, String resumeId);
}
