package com.jdres.repository;

import com.jdres.model.JobDescription;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobDescriptionRepository extends MongoRepository<JobDescription, String> {
    Optional<JobDescription> findByJdId(String jdId);

    List<JobDescription> findByRecruiterId(String recruiterId);
}
