package com.jdres.repository;

import com.jdres.model.Resume;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends MongoRepository<Resume, String> {
    Optional<Resume> findByFileId(String fileId);

    List<Resume> findByRecruiterId(String recruiterId);

    // Resume Isolation
    List<Resume> findByJdId(String jdId);

    void deleteByFileId(String fileId);
}
