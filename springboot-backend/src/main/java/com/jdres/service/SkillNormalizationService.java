package com.jdres.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for normalizing skill names to enable better matching between
 * Job Descriptions and Resumes.
 * 
 * Handles:
 * - Synonym mapping (Node.js = NodeJS = node)
 * - Case normalization
 * - Abbreviation expansion (k8s = kubernetes)
 * - Technology grouping
 */
@Slf4j
@Service
public class SkillNormalizationService {

    // Skill synonym groups - all variations map to the canonical (first) name
    private static final Map<String, List<String>> SKILL_SYNONYMS = new LinkedHashMap<>();
    
    // Reverse lookup: variation -> canonical name
    private static final Map<String, String> NORMALIZATION_MAP = new HashMap<>();

    static {
        // Initialize skill synonym groups
        initializeSkillGroups();
        // Build reverse lookup map
        buildNormalizationMap();
    }

    private static void initializeSkillGroups() {
        // Programming Languages
        addSkillGroup("javascript", "js", "ecmascript", "es6", "es2015", "es2020");
        addSkillGroup("typescript", "ts");
        addSkillGroup("python", "python3", "py");
        addSkillGroup("java", "java8", "java11", "java17", "java21", "jdk", "jre");
        addSkillGroup("c#", "csharp", "c-sharp", "dotnet", ".net", ".net core", "dotnet core");
        addSkillGroup("go", "golang");
        addSkillGroup("rust", "rust-lang");
        addSkillGroup("kotlin", "kt");
        addSkillGroup("swift", "ios swift");
        addSkillGroup("objective-c", "objc", "objective c");
        addSkillGroup("ruby", "ruby on rails", "ror", "rails");
        addSkillGroup("php", "php7", "php8", "laravel", "symfony");
        addSkillGroup("scala", "scala3");
        addSkillGroup("r", "r-lang", "rstats");

        // Frontend Frameworks
        addSkillGroup("react", "reactjs", "react.js", "react js");
        addSkillGroup("angular", "angularjs", "angular.js", "angular2", "angular 2");
        addSkillGroup("vue", "vuejs", "vue.js", "vue3", "vue 3");
        addSkillGroup("svelte", "sveltejs", "svelte.js");
        addSkillGroup("nextjs", "next.js", "next js", "next");
        addSkillGroup("nuxt", "nuxtjs", "nuxt.js");
        addSkillGroup("jquery", "j-query");

        // Backend Frameworks
        addSkillGroup("nodejs", "node.js", "node js", "node");
        addSkillGroup("express", "expressjs", "express.js");
        addSkillGroup("nestjs", "nest.js", "nest js");
        addSkillGroup("spring", "spring boot", "springboot", "spring-boot", "spring framework");
        addSkillGroup("django", "django rest", "drf");
        addSkillGroup("flask", "flask-restful");
        addSkillGroup("fastapi", "fast api", "fast-api");
        addSkillGroup("asp.net", "aspnet", "asp net", "asp.net core", "aspnet core");

        // Databases
        addSkillGroup("postgresql", "postgres", "pg", "psql");
        addSkillGroup("mysql", "mariadb", "maria db");
        addSkillGroup("mongodb", "mongo", "mongo db");
        addSkillGroup("redis", "redis cache", "redis db");
        addSkillGroup("elasticsearch", "elastic search", "es", "elk");
        addSkillGroup("cassandra", "apache cassandra");
        addSkillGroup("dynamodb", "dynamo db", "aws dynamodb");
        addSkillGroup("sql server", "mssql", "ms sql", "microsoft sql");
        addSkillGroup("oracle", "oracle db", "oracledb", "oracle database");
        addSkillGroup("sqlite", "sqlite3");
        addSkillGroup("neo4j", "neo 4j");
        addSkillGroup("couchbase", "couch base");

        // Cloud Platforms
        addSkillGroup("aws", "amazon web services", "amazon aws", "amazon cloud");
        addSkillGroup("azure", "microsoft azure", "ms azure", "azure cloud");
        addSkillGroup("gcp", "google cloud", "google cloud platform", "gcloud");
        addSkillGroup("heroku", "heroku cloud");
        addSkillGroup("digitalocean", "digital ocean", "do");
        addSkillGroup("ibm cloud", "ibm", "bluemix");

        // Container & Orchestration
        addSkillGroup("docker", "docker container", "containerization");
        addSkillGroup("kubernetes", "k8s", "kube", "k8", "kubectl");
        addSkillGroup("openshift", "open shift", "redhat openshift");
        addSkillGroup("docker compose", "docker-compose", "compose");
        addSkillGroup("helm", "helm charts");
        addSkillGroup("istio", "istio service mesh");
        addSkillGroup("podman", "pod man");

        // CI/CD
        addSkillGroup("jenkins", "jenkins ci", "jenkinsfile");
        addSkillGroup("gitlab ci", "gitlab-ci", "gitlab cicd", "gitlab");
        addSkillGroup("github actions", "gh actions", "github-actions");
        addSkillGroup("circleci", "circle ci", "circle-ci");
        addSkillGroup("travis ci", "travisci", "travis");
        addSkillGroup("azure devops", "azure pipelines", "ado");
        addSkillGroup("teamcity", "team city");
        addSkillGroup("bamboo", "atlassian bamboo");
        addSkillGroup("argocd", "argo cd", "argo-cd");

        // Infrastructure as Code
        addSkillGroup("terraform", "tf", "hashicorp terraform");
        addSkillGroup("ansible", "ansible playbook");
        addSkillGroup("puppet", "puppet enterprise");
        addSkillGroup("chef", "chef infra");
        addSkillGroup("cloudformation", "cloud formation", "aws cloudformation", "cfn");
        addSkillGroup("pulumi", "pulumi iac");

        // Monitoring & Observability
        addSkillGroup("prometheus", "prometheus monitoring");
        addSkillGroup("grafana", "grafana dashboard");
        addSkillGroup("datadog", "data dog");
        addSkillGroup("new relic", "newrelic");
        addSkillGroup("splunk", "splunk enterprise");
        addSkillGroup("elk stack", "elk", "elastic stack");
        addSkillGroup("jaeger", "jaeger tracing");
        addSkillGroup("kibana", "kibana dashboard");

        // Message Queues
        addSkillGroup("kafka", "apache kafka", "confluent kafka");
        addSkillGroup("rabbitmq", "rabbit mq", "rabbit");
        addSkillGroup("sqs", "aws sqs", "amazon sqs");
        addSkillGroup("activemq", "active mq", "apache activemq");
        addSkillGroup("redis pub/sub", "redis pubsub", "redis queue");

        // API & Protocols
        addSkillGroup("rest", "restful", "rest api", "restful api");
        addSkillGroup("graphql", "graph ql");
        addSkillGroup("grpc", "g-rpc", "google rpc");
        addSkillGroup("soap", "soap api", "soap services");
        addSkillGroup("websocket", "websockets", "ws", "socket.io");

        // Testing
        addSkillGroup("junit", "junit5", "junit4");
        addSkillGroup("jest", "jestjs");
        addSkillGroup("pytest", "py.test");
        addSkillGroup("selenium", "selenium webdriver");
        addSkillGroup("cypress", "cypress.io");
        addSkillGroup("playwright", "ms playwright");
        addSkillGroup("mocha", "mochajs");
        addSkillGroup("testng", "test ng");

        // Methodologies
        addSkillGroup("agile", "agile methodology", "agile development");
        addSkillGroup("scrum", "scrum master", "scrum methodology");
        addSkillGroup("kanban", "kanban board");
        addSkillGroup("safe", "scaled agile", "safe framework", "scaled agile framework");
        addSkillGroup("devops", "dev ops", "devops culture");
        addSkillGroup("ci/cd", "cicd", "ci cd", "continuous integration", "continuous deployment");
        addSkillGroup("tdd", "test driven development", "test-driven development");
        addSkillGroup("bdd", "behavior driven development", "behaviour driven development");
        addSkillGroup("waterfall", "waterfall methodology");

        // Project Management Tools
        addSkillGroup("jira", "atlassian jira");
        addSkillGroup("confluence", "atlassian confluence");
        addSkillGroup("trello", "trello board");
        addSkillGroup("asana", "asana project");
        addSkillGroup("monday", "monday.com");
        addSkillGroup("azure boards", "azure devops boards");

        // Version Control
        addSkillGroup("git", "git version control", "gitflow");
        addSkillGroup("github", "git hub");
        addSkillGroup("bitbucket", "bit bucket", "atlassian bitbucket");
        addSkillGroup("gitlab", "git lab");
        addSkillGroup("svn", "subversion", "apache subversion");

        // Machine Learning / AI
        addSkillGroup("tensorflow", "tensor flow", "tf", "tensorflow2");
        addSkillGroup("pytorch", "py torch", "torch");
        addSkillGroup("scikit-learn", "sklearn", "scikit learn");
        addSkillGroup("keras", "tf keras");
        addSkillGroup("opencv", "open cv", "cv2");
        addSkillGroup("nlp", "natural language processing");
        addSkillGroup("ml", "machine learning");
        addSkillGroup("ai", "artificial intelligence");
        addSkillGroup("llm", "large language model", "large language models");

        // Data Engineering
        addSkillGroup("spark", "apache spark", "pyspark");
        addSkillGroup("hadoop", "apache hadoop", "hdfs");
        addSkillGroup("airflow", "apache airflow");
        addSkillGroup("dbt", "data build tool");
        addSkillGroup("snowflake", "snowflake db");
        addSkillGroup("databricks", "data bricks");
        addSkillGroup("etl", "extract transform load");

        // Security
        addSkillGroup("oauth", "oauth2", "oauth 2.0");
        addSkillGroup("jwt", "json web token", "json web tokens");
        addSkillGroup("ssl/tls", "ssl", "tls", "https");
        addSkillGroup("owasp", "owasp top 10");
        addSkillGroup("penetration testing", "pen testing", "pentesting");
        addSkillGroup("sso", "single sign on", "single sign-on");

        // Architecture
        addSkillGroup("microservices", "micro services", "micro-services", "microservice architecture");
        addSkillGroup("serverless", "faas", "function as a service");
        addSkillGroup("event-driven", "event driven", "eda", "event-driven architecture");
        addSkillGroup("soa", "service oriented architecture");
        addSkillGroup("domain-driven design", "ddd", "domain driven design");
        addSkillGroup("clean architecture", "hexagonal architecture", "ports and adapters");
    }

    private static void addSkillGroup(String canonical, String... variations) {
        List<String> allVariations = new ArrayList<>();
        allVariations.add(canonical.toLowerCase());
        for (String v : variations) {
            allVariations.add(v.toLowerCase());
        }
        SKILL_SYNONYMS.put(canonical.toLowerCase(), allVariations);
    }

    private static void buildNormalizationMap() {
        for (Map.Entry<String, List<String>> entry : SKILL_SYNONYMS.entrySet()) {
            String canonical = entry.getKey();
            for (String variation : entry.getValue()) {
                NORMALIZATION_MAP.put(variation, canonical);
            }
        }
        // Note: Using System.out because this runs in static context before @Slf4j log is available
        System.out.println("✅ Skill Normalization initialized with " + SKILL_SYNONYMS.size() + 
                " canonical skills and " + NORMALIZATION_MAP.size() + " total mappings");
    }

    /**
     * Normalize a single skill name to its canonical form
     */
    public String normalizeSkill(String skill) {
        if (skill == null || skill.isBlank()) {
            return skill;
        }
        String lowered = skill.toLowerCase().trim();
        return NORMALIZATION_MAP.getOrDefault(lowered, lowered);
    }

    /**
     * Normalize a list of skills to their canonical forms
     */
    public List<String> normalizeSkills(List<String> skills) {
        if (skills == null) return List.of();
        return skills.stream()
                .map(this::normalizeSkill)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Normalize skills from a map of skill categories
     */
    @SuppressWarnings("unchecked")
    public List<String> normalizeSkillsFromMap(Map<String, Object> skillsMap) {
        List<String> allSkills = new ArrayList<>();
        if (skillsMap == null) return allSkills;
        
        for (Object value : skillsMap.values()) {
            if (value instanceof List) {
                allSkills.addAll((List<String>) value);
            }
        }
        return normalizeSkills(allSkills);
    }

    /**
     * Calculate skill match with normalization
     * Returns: { matchedSkills, matchPercentage }
     */
    public SkillMatchResult calculateSkillMatch(List<String> jdSkills, List<String> resumeSkills) {
        List<String> normalizedJD = normalizeSkills(jdSkills);
        List<String> normalizedResume = normalizeSkills(resumeSkills);
        
        Set<String> resumeSet = new HashSet<>(normalizedResume);
        List<String> matchedSkills = normalizedJD.stream()
                .filter(resumeSet::contains)
                .collect(Collectors.toList());
        
        double matchPercentage = normalizedJD.isEmpty() ? 0 
                : (double) matchedSkills.size() / normalizedJD.size() * 100;
        
        return new SkillMatchResult(matchedSkills, normalizedJD.size(), matchPercentage);
    }

    /**
     * Calculate weighted skill match:
     * - Mandatory skills: 100% weight
     * - Tools/Platforms: 75% weight
     * - Preferred skills: 50% weight
     * - Methodologies: 25% weight
     */
    public WeightedSkillMatchResult calculateWeightedMatch(
            List<String> mandatorySkills,
            List<String> preferredSkills,
            List<String> tools,
            List<String> methodologies,
            List<String> resumeSkills) {
        
        Set<String> normalizedResume = new HashSet<>(normalizeSkills(resumeSkills));
        
        List<String> matchedMandatory = normalizeSkills(mandatorySkills).stream()
                .filter(normalizedResume::contains).collect(Collectors.toList());
        List<String> matchedPreferred = normalizeSkills(preferredSkills).stream()
                .filter(normalizedResume::contains).collect(Collectors.toList());
        List<String> matchedTools = normalizeSkills(tools).stream()
                .filter(normalizedResume::contains).collect(Collectors.toList());
        List<String> matchedMethodologies = normalizeSkills(methodologies).stream()
                .filter(normalizedResume::contains).collect(Collectors.toList());
        
        // Calculate weighted score
        double mandatoryScore = mandatorySkills.isEmpty() ? 0 
                : (double) matchedMandatory.size() / mandatorySkills.size() * 100;
        double preferredScore = preferredSkills.isEmpty() ? 0 
                : (double) matchedPreferred.size() / preferredSkills.size() * 50; // 50% weight
        double toolsScore = tools.isEmpty() ? 0 
                : (double) matchedTools.size() / tools.size() * 75; // 75% weight
        double methodScore = methodologies.isEmpty() ? 0 
                : (double) matchedMethodologies.size() / methodologies.size() * 25; // 25% weight
        
        // Combine: mandatory has full weight, others are bonuses
        double weightedScore = mandatoryScore; // Base is mandatory match %
        if (!preferredSkills.isEmpty()) weightedScore += preferredScore * 0.15; // 15% bonus possible from preferred
        if (!tools.isEmpty()) weightedScore += toolsScore * 0.10; // 10% bonus possible from tools
        if (!methodologies.isEmpty()) weightedScore += methodScore * 0.05; // 5% bonus possible from methodologies
        
        // Cap at 100
        weightedScore = Math.min(100, weightedScore);
        
        return new WeightedSkillMatchResult(
                matchedMandatory, matchedPreferred, matchedTools, matchedMethodologies,
                mandatoryScore, weightedScore
        );
    }

    /**
     * Get all known variations for a canonical skill
     */
    public List<String> getSkillVariations(String canonicalSkill) {
        return SKILL_SYNONYMS.getOrDefault(canonicalSkill.toLowerCase(), List.of(canonicalSkill));
    }

    /**
     * Check if two skills are equivalent (after normalization)
     */
    public boolean areSkillsEquivalent(String skill1, String skill2) {
        return normalizeSkill(skill1).equals(normalizeSkill(skill2));
    }

    // Result classes
    public static class SkillMatchResult {
        public final List<String> matchedSkills;
        public final int totalJDSkills;
        public final double matchPercentage;

        public SkillMatchResult(List<String> matchedSkills, int totalJDSkills, double matchPercentage) {
            this.matchedSkills = matchedSkills;
            this.totalJDSkills = totalJDSkills;
            this.matchPercentage = matchPercentage;
        }
    }

    public static class WeightedSkillMatchResult {
        public final List<String> matchedMandatory;
        public final List<String> matchedPreferred;
        public final List<String> matchedTools;
        public final List<String> matchedMethodologies;
        public final double mandatoryMatchPercentage;
        public final double weightedScore;

        public WeightedSkillMatchResult(
                List<String> matchedMandatory,
                List<String> matchedPreferred,
                List<String> matchedTools,
                List<String> matchedMethodologies,
                double mandatoryMatchPercentage,
                double weightedScore) {
            this.matchedMandatory = matchedMandatory;
            this.matchedPreferred = matchedPreferred;
            this.matchedTools = matchedTools;
            this.matchedMethodologies = matchedMethodologies;
            this.mandatoryMatchPercentage = mandatoryMatchPercentage;
            this.weightedScore = weightedScore;
        }
    }
}
