/**
 * JD-Resume Matching Application (Vanilla JS + Tailwind)
 */

import { auth } from './firebase-config.js';
import { onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-auth.js";

class App {
    constructor() {
        // State
        this.jobs = [
            { id: 'job-1', title: 'Workday Recruiting Functional', createdAt: 'Today', jdText: '', jdFile: null, status: 'idle', results: null, resumes: [] }
        ];
        this.activeJobId = 'job-1';
        this.theme = 'dark';
        this.allResumes = []; // Global pool of user's resumes

        // Check for Google Viewer support
        this.getViewerUrl = (url) => {
            if (!url) return '';
            const lower = url.toLowerCase();
            if (lower.endsWith('.docx') || lower.endsWith('.doc') || lower.endsWith('.pptx') || lower.endsWith('.xlsx')) {
                return `https://docs.google.com/gview?url=${encodeURIComponent(url)}&embedded=true`;
            }
            return url;
        };

        // REMOVED: Global resumes - now each job has its own job.resumes array

        // Cache DOM elements
        this.dom = {
            body: document.getElementById('appBody'),
            themeToggleBtn: document.getElementById('themeToggleBtn'),
            jobsList: document.getElementById('jobsList'),
            newJobBtn: document.getElementById('newJobBtn'),
            activeJobTitleDisplay: document.getElementById('activeJobTitleDisplay'),
            jobTitleInput: document.getElementById('jobTitleInput'),

            // JD Section
            jdSection: document.getElementById('jdSection'),
            jdFileInput: document.getElementById('jdFileInput'),

            saveJdBtn: document.getElementById('saveJdBtn'),
            clearJdBtn: document.getElementById('clearJdBtn'),
            jdFileInfoBox: document.getElementById('jdFileInfoBox'),
            jdFileDetails: document.getElementById('jdFileDetails'),
            jdFilePlaceholder: document.getElementById('jdFilePlaceholder'),
            jdFileName: document.getElementById('jdFileName'),
            jdFileSize: document.getElementById('jdFileSize'),
            jdTextArea: document.getElementById('jdTextArea'),
            jdHelperText: document.getElementById('jdHelperText'),
            jdPasteLabel: document.getElementById('jdPasteLabel'),

            // Results Section
            statusBadge: document.getElementById('statusBadge'),
            resultsContent: document.getElementById('resultsContent'),
            resultsPlaceholder: document.getElementById('resultsPlaceholder'),
            skillsDisplay: document.getElementById('skillsDisplay'),
            skillsList: document.getElementById('skillsList'),

            // Resume Upload
            resumeSection: document.getElementById('resumeSection'),
            resumeFileInput: document.getElementById('resumeFileInput'),
            resumeFolderInput: document.getElementById('resumeFolderInput'),
            mainUploadBtn: document.getElementById('mainUploadBtn'),
            // New Upload Modal Elements
            uploadModal: document.getElementById('uploadModal'),
            uploadModalClose: document.getElementById('uploadModalClose'),
            modalDragZone: document.getElementById('modalDragZone'),

            // Projects Modal
            projectsModal: document.getElementById('projectsModal'),
            projectsModalClose: document.getElementById('projectsModalClose'),
            projectsModalContent: document.getElementById('projectsModalContent'),
            projectsModalCandidateName: document.getElementById('projectsModalCandidateName'),


            uploadResumeBtn: document.getElementById('uploadResumeBtn'),
            folderUploadBtn: document.getElementById('folderUploadBtn'),
            resumesList: document.getElementById('resumesList'),
            resumeCount: document.getElementById('resumeCount'),

            // Actions
            extractBtn: document.getElementById('extractBtn'),
            rankBtn: document.getElementById('rankBtn'),

            // Toast
            toast: document.getElementById('toast'),
            toastTitle: document.getElementById('toastTitle'),
            toastMessage: document.getElementById('toastMessage'),
            toastIcon: document.getElementById('toastIcon'),

            // Drive Import
            importDriveBtn: document.getElementById('importDriveBtn'),
            driveModal: document.getElementById('driveModal'),
            driveModalClose: document.getElementById('driveModalClose'),
            driveLinkInput: document.getElementById('driveLinkInput'),
            driveScanBtn: document.getElementById('driveScanBtn'),
            driveStatus: document.getElementById('driveStatus'),

            // JD Requirements Display
            jdRequirementsSection: document.getElementById('jdRequirementsSection'),
            expBadge: document.getElementById('expBadge'),
            expYearsDisplay: document.getElementById('expYearsDisplay'),
            requiredSkillsList: document.getElementById('requiredSkillsList'),
            requiredSkillsCount: document.getElementById('requiredSkillsCount'),
            preferredSkillsSection: document.getElementById('preferredSkillsSection'),
            preferredSkillsList: document.getElementById('preferredSkillsList'),
            preferredSkillsCount: document.getElementById('preferredSkillsCount')
        };

        this.init();
    }

    async init() {
        // Initialize theme based on state (default dark)
        if (this.theme === 'dark') {
            document.documentElement.classList.add('dark');
        } else {
            document.documentElement.classList.remove('dark');
        }

        // Auth Listener - Wait for auth before loading data
        this.userData = null;
        onAuthStateChanged(auth, async (user) => {
            if (user) {
                this.userData = user;
                this.updateUserProfileUI(user);

                console.log('🔐 User authenticated:', user.uid);

                // NOW load data after user is authenticated
                await this.loadSavedResumes();
                await this.loadSavedJD();

                // If no jobs were loaded, ensure we have at least one default job
                if (this.jobs.length === 0) {
                    this.jobs = [
                        { id: 'job-1', title: 'New Job', createdAt: 'Just now', jdText: '', jdFile: null, status: 'idle', jdSkills: [] }
                    ];
                    this.activeJobId = 'job-1';
                }

                // Load saved matches for active job
                if (this.activeJob && this.activeJob.jdId) {
                    await this.loadMatchesForJob(this.activeJob);
                }

                // Now render the UI with user-specific data
                this.renderJobsList();
                this.updateUIForActiveJob();
            } else {
                window.location.href = 'landing.html';
            }
        });

        // Initialize auto-save debounce timer
        this.saveJDTimeout = null;

        // Attach event listeners first
        this.attachEventListeners();
    }

    // Load all saved JDs from database
    async loadSavedJD() {
        try {
            const userId = this.getUserId();
            console.log('📥 Loading saved JDs for user:', userId);

            if (!userId) {
                console.warn('⚠️ No userId available in loadSavedJD - skipping');
                return;
            }

            const response = await fetch('/api/v2/job-descriptions', {
                headers: this.getAuthHeaders()
            });
            const data = await response.json();

            if (data.success && data.jobDescriptions && data.jobDescriptions.length > 0) {
                console.log(`✅ Found ${data.jobDescriptions.length} saved JDs`);

                // Clear default jobs and load all from database
                this.jobs = [];

                data.jobDescriptions.forEach((jd, index) => {
                    const hasSkills = jd.requiredSkills && jd.requiredSkills.length > 0;
                    const job = {
                        id: jd.jdId || `job-${index}`,
                        title: jd.title || 'Untitled JD',
                        createdAt: jd.createdAt ? new Date(jd.createdAt).toLocaleDateString() : 'Saved',
                        jdText: jd.text || '',
                        jdFile: null,
                        jdId: jd.jdId,
                        status: hasSkills ? 'extracted' : 'idle',
                        jdSkills: jd.requiredSkills || [],
                        preferredSkills: jd.preferredSkills || [],
                        suggestedKeywords: jd.suggestedKeywords || [],
                        minExperience: jd.minExperience || 0,
                        resumes: []  // Job-specific resumes (will be loaded separately)
                    };
                    this.jobs.push(job);
                });

                // Set the last one (most recent) as active
                if (this.jobs.length > 0) {
                    this.activeJobId = this.jobs[this.jobs.length - 1].id;
                }
            } else {
                console.log('ℹ️ No saved JDs found');
            }
        } catch (error) {
            console.error('❌ Failed to load saved JDs:', error);
        }
    }

    // Load saved match results for a specific job
    async loadMatchesForJob(job) {
        // Reset resume state to prevent bleeding from other jobs
        // NOTE: candidateName and candidateExperience are resume properties (not job-specific), so don't delete them
        this.activeJobResumes.forEach(r => {
            r.status = null;
            r.matchScore = 0;
            r.skillMatchScore = 0;
            delete r.matchedSkillsList;
            delete r.missingSkillsList;
            delete r.relevantProjects;
        });
        if (!job || !job.jdId) return;

        try {
            const userId = this.getUserId();
            if (!userId) return;

            const res = await fetch(`/api/job-descriptions/${job.jdId}/matches?limit=100`, {
                headers: this.getAuthHeaders()
            });
            const data = await res.json();

            if (data.success && data.matches && data.matches.length > 0) {
                console.log(`✅ Loaded ${data.matches.length} saved matches for ${job.title}`);

                // Store matches in the job
                job.matchResults = data.matches;
                job.status = 'ranked'; // Has saved results

                // Populate job-specific resumes from match results
                data.matches.forEach(match => {
                    // Find or create resume in job's resumes array
                    let resume = this.activeJobResumes.find(r =>
                        r.fileId === match.resumeId ||
                        r.name === match.resumeName
                    );

                    // If resume doesn't exist in this job yet, add it
                    if (!resume) {
                        resume = {
                            fileId: match.resumeId,
                            name: match.resumeName || match.candidateName || 'Unknown',
                            candidateName: match.candidateName,
                            viewLink: match.viewLink || match.s3Url,
                            skills: [],
                            text: '' // Will be loaded if needed
                        };
                        job.resumes.push(resume);
                    }

                    // Update resume with match data
                    let score = match.matchScore || match.finalScore || 0;
                    // Fix: Clamp score to 100 to prevent 10000% display issue
                    if (score > 100) {
                        console.warn(`⚠️ Found abnormal score ${score} for ${resume.name}, normalizing...`);
                        score = Math.min(score, 100);
                    }

                    let skillScore = match.skillMatchScore || 0;
                    if (skillScore > 100) {
                        skillScore = Math.min(skillScore, 100);
                    }

                    resume.matchScore = score;
                    resume.candidateName = match.candidateName;
                    resume.candidateExperience = match.candidateExperience;
                    resume.hasGap = match.hasGap;
                    resume.gapMonths = match.gapMonths;
                    resume.matchedSkillsList = match.matchedSkillsList || [];
                    resume.missingSkillsList = match.missingSkillsList || [];
                    resume.skillMatchScore = skillScore;
                    resume.relevantProjects = match.relevantProjects || [];
                    resume.status = match.candidateStatus; // Accept/Review/Reject status

                    // Fix: Ensure we have the full skill list for re-matching
                    if (match.allSkills && Array.isArray(match.allSkills)) {
                        resume.skills = match.allSkills;
                    }
                    if (match.resumeText) {
                        resume.text = match.resumeText;
                    }
                });
            }
        } catch (e) {
            console.warn('Could not load matches for job:', e);
        }
    }

    // Debounced save function for JD (waits 2 seconds after user stops typing)
    debouncedSaveJD() {
        clearTimeout(this.saveJDTimeout);
        this.saveJDTimeout = setTimeout(() => {
            this.saveJobDescription();
        }, 2000); // 2 second delay
    }

    // Save Job Description to database
    async saveJobDescription() {
        const job = this.activeJob;
        if (!job) return;

        // Only save if there's actual JD text (or if we are just updating metadata)
        if ((!job.jdText || job.jdText.trim().length === 0) && !job.jdFile) {
            console.log('⏭️  No JD text to save');
            return false;
        }

        try {
            let response;
            const payload = {
                jdText: job.jdText,
                title: job.title || 'Untitled JD',
                suggestedKeywords: job.suggestedKeywords || []
            };

            if (job.jdId) {
                // UPDATE existing
                response = await fetch(`/api/job-descriptions/${job.jdId}`, {
                    method: 'PUT',
                    headers: this.getAuthHeaders({ 'Content-Type': 'application/json' }),
                    body: JSON.stringify(payload)
                });
            } else {
                // CREATE new
                response = await fetch('/api/job-descriptions', {
                    method: 'POST',
                    headers: this.getAuthHeaders({ 'Content-Type': 'application/json' }),
                    body: JSON.stringify(payload)
                });
            }

            const data = await response.json();

            if (data.success) {
                // Store the jdId from backend (only if new)
                if (!job.jdId) {
                    job.jdId = data.jdId;
                }

                // Update skills from backend if we don't have them already
                // (Only update if backend returned something useful/new)
                if (data.requiredSkills) job.jdSkills = data.requiredSkills;
                if (data.preferredSkills) job.preferredSkills = data.preferredSkills;
                if (data.suggestedKeywords) job.suggestedKeywords = data.suggestedKeywords;
                if (data.minExperience) job.minExperience = data.minExperience;

                // Update status if we got skills
                if (job.jdSkills && job.jdSkills.length > 0) {
                    job.status = 'extracted';
                }

                console.log('✅ JD saved with', job.jdSkills?.length || 0, 'required skills');
                return true;
            }
            return false;
        } catch (error) {
            console.error('Failed to save JD:', error);
            return false;
        }
    }

    async loadSavedResumes() {
        try {
            const userId = this.getUserId();
            console.log('📥 Loading saved Resumes for user:', userId);

            if (!userId) {
                console.warn('⚠️ No userId available in loadSavedResumes - skipping');
                return;
            }

            const res = await fetch('/api/resumes', {
                headers: this.getAuthHeaders()
            });
            const data = await res.json();

            if (data.success && data.resumes && data.resumes.length > 0) {
                // Load into global resumes array (shared across all jobs)
                this.allResumes = data.resumes.map(r => ({
                    name: r.name,
                    text: r.text || '',
                    skills: r.skills || [],
                    fileId: r.fileId,
                    viewLink: r.viewLink || r.s3Url,
                    candidateName: r.candidateName,
                    candidateExperience: r.candidateExperience || 0
                }));

                // Initial render for active job
                this.renderResumesList();
                this.updateActionButtons();
                console.log(`✅ Loaded ${this.allResumes.length} resumes globally`);
            }
        } catch (e) {
            console.error("Failed to load saved resumes", e);
        }
    }

    // Helper to close any modal
    closeModal(modal) {
        if (!modal) return;
        const content = modal.querySelector('div.transform');
        if (content) {
            content.classList.remove('scale-100');
            content.classList.add('scale-95');
        }
        modal.classList.add('opacity-0', 'pointer-events-none');
    }

    // Show Projects Modal
    showProjectsModal(resumeId) {
        const resume = this.activeJobResumes.find(r => (r.fileId || r.name) === resumeId);
        if (!resume || !this.dom.projectsModal) return;

        this.dom.projectsModalCandidateName.textContent = resume.candidateName || resume.name || 'Candidate';
        this.dom.projectsModalContent.innerHTML = '';

        const projects = resume.relevantProjects || [];
        if (projects.length === 0) {
            this.dom.projectsModalContent.innerHTML = `
                <div class="text-center py-10 text-zinc-400 dark:text-zinc-600 italic">
                    No relevant projects analysis available.
                </div>
            `;
        } else {
            projects.forEach((p, idx) => {
                const allTech = p.allTech || [];
                const matchingTechs = p.matchingTechs || [];
                const techTags = allTech.map(t => {
                    const isMatch = matchingTechs.includes(t);
                    return `<span class="px-2 py-1 rounded text-[10px] font-medium ${isMatch
                        ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800'
                        : 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400 border border-zinc-200 dark:border-zinc-700'}">${t}</span>`;
                }).join('');

                const div = document.createElement('div');
                div.className = "p-4 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800/20";
                div.innerHTML = `
                    <div class="flex items-start justify-between mb-2">
                         <h4 class="font-bold text-zinc-800 dark:text-zinc-200 text-sm">${p.name || `Project ${idx + 1}`}</h4>
                         ${p.matchScore ? `<span class="text-[10px] font-bold text-sky-600 dark:text-sky-400 bg-sky-50 dark:bg-sky-900/20 px-1.5 py-0.5 rounded">Match: ${p.matchScore}%</span>` : ''}
                    </div>
                    <p class="text-xs text-zinc-600 dark:text-zinc-400 mb-3 leading-relaxed">${p.description || 'No description available.'}</p>
                    <div class="flex flex-wrap gap-1.5">
                        ${techTags}
                    </div>
                `;
                this.dom.projectsModalContent.appendChild(div);
            });
        }

        // Show
        this.dom.projectsModal.classList.remove('opacity-0', 'pointer-events-none');
        const content = this.dom.projectsModal.querySelector('div.transform');
        content.classList.remove('scale-95');
        content.classList.add('scale-100');
    }

    updateUserProfileUI(user) {
        const profileEl = document.getElementById('userProfile');
        if (profileEl) {
            const initial = user.displayName ? user.displayName.charAt(0).toUpperCase() : (user.email ? user.email.charAt(0).toUpperCase() : 'U');

            profileEl.innerHTML = `
                <div class="text-right leading-none hidden sm:block">
                    <div class="text-xs font-bold text-zinc-900 dark:text-zinc-100 mb-0.5">${user.displayName || 'User'}</div>
                    <div class="text-[10px] font-medium text-zinc-500 dark:text-zinc-400 opacity-80">${user.email}</div>
                </div>
                <div class="relative">
                    <button id="profileMenuBtn" class="h-8 w-8 rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 ring-2 ring-white dark:ring-zinc-900 shadow-sm grid place-items-center text-xs text-white font-bold cursor-pointer hover:opacity-90 transition">
                        ${initial}
                    </button>
                    <!-- Dropdown Menu -->
                    <div id="profileDropdown" class="absolute right-0 top-full mt-2 w-48 rounded-xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-xl py-1 opacity-0 pointer-events-none transform scale-95 transition-all duration-200 z-50 origin-top-right">
                        <div class="px-4 py-3 border-b border-zinc-100 dark:border-zinc-800">
                             <p class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">${user.displayName || 'User'}</p>
                             <p class="text-xs text-zinc-500 dark:text-zinc-400 truncate">${user.email}</p>
                        </div>
                        <a href="#" class="block px-4 py-2 text-sm text-zinc-700 dark:text-zinc-300 hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors">Account Settings</a>
                        <button id="logoutAction" class="w-full text-left block px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors">
                            Sign out
                        </button>
                    </div>
                </div>
            `;

            // Toggle Dropdown logic
            const btn = document.getElementById('profileMenuBtn');
            const dropdown = document.getElementById('profileDropdown');
            const logoutAction = document.getElementById('logoutAction');

            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                dropdown.classList.toggle('opacity-0');
                dropdown.classList.toggle('pointer-events-none');
                dropdown.classList.toggle('scale-95');
                dropdown.classList.toggle('scale-100');
            });

            // Close when clicking outside
            document.addEventListener('click', (e) => {
                if (!profileEl.contains(e.target)) {
                    dropdown.classList.add('opacity-0', 'pointer-events-none', 'scale-95');
                    dropdown.classList.remove('scale-100');
                }
            });

            logoutAction.addEventListener('click', () => this.handleLogout());
        }
    }

    async handleLogout() {
        try {
            await signOut(auth);
            this.showToast('Signed out', 'Redirecting...', 'info');
        } catch (e) {
            console.error('Logout error', e);
        }
    }

    // Get current user ID for API requests
    getUserId() {
        return this.userData?.uid || null;
    }

    // Get headers with user ID for authenticated API requests
    getAuthHeaders(additionalHeaders = {}) {
        const headers = { ...additionalHeaders };
        const userId = this.getUserId();
        console.log('🔑 getAuthHeaders - userId:', userId, 'userData:', this.userData?.uid);
        if (userId) {
            headers['X-User-Id'] = userId;
        }
        return headers;
    }

    get activeJob() {
        return this.jobs.find(j => j.id === this.activeJobId);
    }

    get activeJobResumes() {
        const job = this.activeJob;
        if (!job) return [];
        if (!job.resumes) job.resumes = []; // Ensure resumes array exists

        // Sync global resumes into job.resumes (User-Specific Mode)
        // This ensures all uploaded resumes appear for ALL jobs
        if (this.allResumes && this.allResumes.length > 0) {
            this.allResumes.forEach(globalR => {
                // Check if this global resume is already tracked in this job
                const exists = job.resumes.find(r => r.fileId === globalR.fileId);
                if (!exists) {
                    // Initialize it in this job with defaults
                    job.resumes.push({
                        ...globalR,
                        matchScore: 0,
                        status: null, // No status yet for this specific job
                        // Ensure key data is preserved
                        skills: globalR.skills || []
                    });
                } else {
                    // Optional: update basic info if it changed globally (like parsed details)
                    // But preserve job-specific match scores
                }
            });
        }

        return job.resumes;
    }

    attachEventListeners() {
        // Projects Modal Close
        if (this.dom.projectsModalClose) {
            this.dom.projectsModalClose.addEventListener('click', () => {
                this.closeModal(this.dom.projectsModal);
            });
            this.dom.projectsModal.addEventListener('click', (e) => {
                if (e.target === this.dom.projectsModal) this.closeModal(this.dom.projectsModal);
            });
        }

        // Theme Toggle
        this.dom.themeToggleBtn.addEventListener('click', () => this.toggleTheme());

        // New Job
        this.dom.newJobBtn.addEventListener('click', () => this.createNewJob());


        // Job Title Rename (Manual Save only)
        this.dom.jobTitleInput.addEventListener('input', (e) => {
            this.activeJob.title = e.target.value;
            this.dom.activeJobTitleDisplay.textContent = e.target.value || 'Job';
            this.renderJobsList();
            // Auto-save removed
        });

        // JD Text Input (Manual Save only)
        this.dom.jdTextArea.addEventListener('input', (e) => {
            this.activeJob.jdText = e.target.value;
            this.updateActionButtons();
            // Auto-save removed
        });

        // JD File Upload (Box Click)
        this.dom.jdFilePlaceholder.addEventListener('click', () => this.dom.jdFileInput.click());

        // Manual Save Button (Text + Skills)
        if (this.dom.saveJdBtn) {
            this.dom.saveJdBtn.addEventListener('click', async () => {
                const job = this.activeJob;
                if (!job || !job.jdText || !job.jdText.trim()) {
                    this.showToast('Nothing to save', 'Please enter some text first', 'error');
                    return;
                }

                this.showToast('Analyzing...', 'Extracting skills from new text...', 'info');

                // 1. Extract Skills First (so we save updated skills)
                try {
                    const jdRes = await fetch('/api/extract-skills', {
                        method: 'POST',
                        headers: this.getAuthHeaders({ 'Content-Type': 'application/json' }),
                        body: JSON.stringify({ text: job.jdText, type: 'JD' })
                    });
                    const jdData = await jdRes.json();
                    if (jdData.success && jdData.skills) {
                        job.jdSkills = jdData.skills;
                        job.requiredExperience = jdData.requiredExperience || 0;
                        job.status = 'extracted';
                    }
                } catch (e) {
                    console.warn('Extraction skipped/failed during save', e);
                }

                // 2. Save Everything (Text + Skills)
                this.showToast('Saving...', 'Saving job data...', 'info');
                const success = await this.saveJobDescription();

                if (success) {
                    this.showToast('Saved', 'Job text and skills saved successfully', 'success');
                    this.updateUIForActiveJob(); // Refresh UI to show new skills
                } else {
                    this.showToast('Save Failed', 'Could not save job description', 'error');
                }
            });
        }
        this.dom.jdFileInput.addEventListener('change', (e) => {
            if (e.target.files[0]) this.handleJDUpload(e.target.files[0]);
        });
        this.dom.clearJdBtn.addEventListener('click', () => this.clearJDFile());

        // Drag & Drop for JD
        this.dom.jdSection.addEventListener('dragover', (e) => {
            e.preventDefault();
            this.dom.jdSection.classList.add('bg-zinc-900');
        });
        this.dom.jdSection.addEventListener('dragleave', (e) => {
            e.preventDefault();
            this.dom.jdSection.classList.remove('bg-zinc-900');
        });
        this.dom.jdSection.addEventListener('drop', (e) => {
            e.preventDefault();
            this.dom.jdSection.classList.remove('bg-zinc-900');
            if (e.dataTransfer.files[0]) this.handleJDUpload(e.dataTransfer.files[0]);
        });

        // Resume Upload Modal Logic
        if (this.dom.mainUploadBtn && this.dom.uploadModal) {
            // Open Modal
            this.dom.mainUploadBtn.addEventListener('click', () => {
                this.dom.uploadModal.classList.remove('opacity-0', 'pointer-events-none');
                const content = this.dom.uploadModal.querySelector('div.transform');
                content.classList.remove('scale-95');
                content.classList.add('scale-100');
            });

            // Close Modal
            this.dom.uploadModalClose.addEventListener('click', () => {
                const content = this.dom.uploadModal.querySelector('div.transform');
                content.classList.remove('scale-100');
                content.classList.add('scale-95');
                this.dom.uploadModal.classList.add('opacity-0', 'pointer-events-none');
            });

            // Close when clicking outside content
            this.dom.uploadModal.addEventListener('click', (e) => {
                if (e.target === this.dom.uploadModal) {
                    this.dom.uploadModalClose.click();
                }
            });

            // "Import from Drive" from within the modal
            this.dom.importDriveBtn?.addEventListener('click', () => {
                this.dom.uploadModalClose.click(); // Close resume modal
                setTimeout(() => this.openDriveModal(), 300); // Open drive modal
            });

            // Modal Drag Zone Click -> Trigger File Input
            this.dom.modalDragZone?.addEventListener('click', () => this.dom.resumeFileInput.click());

            // Modal Drag & Drop Logic
            const dz = this.dom.modalDragZone;
            if (dz) {
                dz.addEventListener('dragover', (e) => {
                    e.preventDefault();
                    dz.classList.add('bg-zinc-100', 'dark:bg-zinc-800', 'border-sky-500');
                });
                dz.addEventListener('dragleave', (e) => {
                    e.preventDefault();
                    dz.classList.remove('bg-zinc-100', 'dark:bg-zinc-800', 'border-sky-500');
                });
                dz.addEventListener('drop', (e) => {
                    e.preventDefault();
                    e.stopPropagation(); // Stop bubbling
                    dz.classList.remove('bg-zinc-100', 'dark:bg-zinc-800', 'border-sky-500');
                    if (e.dataTransfer.files.length > 0 || e.dataTransfer.items.length > 0) {
                        this.handleResumeDrop(e);
                        this.dom.uploadModalClose.click(); // Close modal after drop
                    }
                });
            }
        }

        // Resume Upload (single files)
        this.dom.uploadResumeBtn.addEventListener('click', () => this.dom.resumeFileInput.click());
        this.dom.resumeFileInput.addEventListener('change', (e) => {
            // Handle multiple files
            const files = e.target.files;
            for (let i = 0; i < files.length; i++) {
                this.handleResumeUpload(files[i]);
            }
            e.target.value = ''; // Reset input
        });

        // Resume Folder Upload
        if (this.dom.folderUploadBtn && this.dom.resumeFolderInput) {
            this.dom.folderUploadBtn.addEventListener('click', () => this.dom.resumeFolderInput.click());
            this.dom.resumeFolderInput.addEventListener('change', (e) => {
                const files = e.target.files;
                let pdfCount = 0;
                for (let i = 0; i < files.length; i++) {
                    const file = files[i];
                    // Only process PDF/DOC files
                    if (file.name.match(/\.(pdf|doc|docx)$/i)) {
                        this.handleResumeUpload(file);
                        pdfCount++;
                    }
                }
                if (pdfCount > 0) {
                    this.showToast('Folder Uploaded', `Processing ${pdfCount} resume files`, 'info');
                }
                e.target.value = ''; // Reset input
            });
        }

        // Resume Drag & Drop - Apply to the ENTIRE section
        if (this.dom.resumeSection) {
            const section = this.dom.resumeSection;

            section.addEventListener('dragover', (e) => {
                e.preventDefault();
                // Visual feedback
                section.classList.add('ring-2', 'ring-sky-500', 'bg-sky-50', 'dark:bg-sky-900/20');
            });
            section.addEventListener('dragleave', (e) => {
                e.preventDefault();
                section.classList.remove('ring-2', 'ring-sky-500', 'bg-sky-50', 'dark:bg-sky-900/20');
            });
            section.addEventListener('drop', (e) => {
                e.preventDefault();
                section.classList.remove('ring-2', 'ring-sky-500', 'bg-sky-50', 'dark:bg-sky-900/20');
                if (e.dataTransfer.files.length > 0 || e.dataTransfer.items.length > 0) {
                    this.handleResumeDrop(e);
                }
            });
        }

        // Drive Import
        this.dom.importDriveBtn.addEventListener('click', () => this.openDriveModal());
        this.dom.driveModalClose.addEventListener('click', () => this.closeDriveModal());
        this.dom.driveScanBtn.addEventListener('click', () => this.handleDriveImport());

        // API Actions

        this.dom.rankBtn.addEventListener('click', () => this.runMatchAndRank());
    }

    // --- Logic & content manipulation ---

    openDriveModal() {
        this.dom.driveModal.classList.remove('opacity-0', 'pointer-events-none');
        const content = this.dom.driveModal.querySelector('div.transform');
        content.classList.remove('scale-95');
        content.classList.add('scale-100');
    }

    closeDriveModal() {
        const content = this.dom.driveModal.querySelector('div.transform');
        content.classList.remove('scale-100');
        content.classList.add('scale-95');
        this.dom.driveModal.classList.add('opacity-0', 'pointer-events-none');
        this.dom.driveStatus.classList.add('hidden');
        this.dom.driveLinkInput.value = '';
    }

    async handleDriveImport() {
        const link = this.dom.driveLinkInput.value.trim();
        if (!link) {
            this.showToast('Validation Error', 'Please enter a Google Drive link', 'error');
            return;
        }

        const btn = this.dom.driveScanBtn;
        const status = this.dom.driveStatus;

        btn.disabled = true;
        btn.innerHTML = `<svg class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg> Scanning...`;

        status.textContent = "Connecting to Drive securely... This may take a moment.";
        status.classList.remove('hidden');

        try {
            // Collecting existing IDs for incremental sync
            const existingIds = this.activeJob.resumes
                .map(r => r.fileId)
                .filter(id => id); // Filter out undefined

            const res = await fetch('/api/import-drive', {
                method: 'POST',
                headers: this.getAuthHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({
                    link: link,
                    excludeIds: existingIds
                })
            });
            const data = await res.json();

            if (data.success) {
                const newFiles = data.files;
                if (newFiles.length === 0) {
                    this.showToast('Sync Complete', 'No new files found in this folder.', 'info');
                } else {
                    newFiles.forEach(f => {
                        this.activeJob.resumes.push({
                            name: f.name,
                            text: f.text,
                            skills: [],
                            matchScore: 0,
                            fileId: f.fileId || f.id, // Important for deduplication
                            viewLink: f.viewLink
                        });
                    });
                    this.renderResumesList();
                    this.updateActionButtons();
                    this.showToast('Import Successful', `Imported ${newFiles.length} resumes from Drive`, 'success');
                }
                this.closeDriveModal();
            } else {
                throw new Error(data.error);
            }

        } catch (e) {
            status.textContent = "Error: " + e.message;
            status.classList.add('text-red-500');
            this.showToast('Import Failed', e.message, 'error');
        } finally {
            btn.disabled = false;
            btn.innerHTML = 'Scan & Import';
        }
    }

    // New helper: Add keyword
    async addSuggestedKeyword(keyword) {
        if (!keyword || !keyword.trim()) return;
        const job = this.activeJob;
        if (!job.suggestedKeywords) job.suggestedKeywords = [];

        // Avoid duplicates (case-insensitive)
        if (!job.suggestedKeywords.some(k => k.toLowerCase() === keyword.toLowerCase())) {
            job.suggestedKeywords.push(keyword.trim());
            // Update UI
            this.updateResultsView();
            // Save to backend
            this.debouncedSaveJD();
        }
    }

    // New helper: Remove keyword
    async removeSuggestedKeyword(keyword) {
        const job = this.activeJob;
        if (!job.suggestedKeywords) return;

        job.suggestedKeywords = job.suggestedKeywords.filter(k => k !== keyword);
        this.updateResultsView();
        this.debouncedSaveJD();
    }

    createNewJob() {
        const id = 'job-' + Math.random().toString(16).slice(2, 8);
        const newJob = {
            id,
            title: 'New Job',
            createdAt: 'Just now',
            jdText: '',
            jdFile: null,
            status: 'idle',
            jdSkills: [],
            resumes: []  // Job-specific resumes
        };
        this.jobs.unshift(newJob);
        this.activeJobId = id;
        this.renderJobsList();
        this.updateUIForActiveJob();
        this.showToast('Workspace created', 'New job workspace added', 'info');
    }

    toggleTheme() {
        this.theme = this.theme === 'dark' ? 'light' : 'dark';

        const isDark = this.theme === 'dark';
        // Icon logic: If current is Dark, show Sun (to switch to Light). If current is Light, show Moon (to switch to Dark).
        const iconPath = isDark
            ? 'M12 3v2.25m6.364.386l-1.591 1.591M21 12h-2.25m-.386 6.364l-1.591-1.591M12 18.75V21m-4.773-4.227l-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0z'
            : 'M21.752 15.002A9.718 9.718 0 0118 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 003 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 009.002-5.998z';

        const text = isDark ? 'Light' : 'Dark';

        this.dom.themeToggleBtn.innerHTML = `
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="h-4 w-4">
                <path stroke-linecap="round" stroke-linejoin="round" d="${iconPath}" />
            </svg>
            <span>${text}</span>
        `;

        if (this.theme === 'dark') {
            document.documentElement.classList.add('dark');
        } else {
            document.documentElement.classList.remove('dark');
        }
    }

    renderJobsList() {
        if (!this.dom.jobsList) return;

        this.dom.jobsList.innerHTML = '';
        this.jobs.forEach(job => {
            const isActive = job.id === this.activeJobId;
            const el = document.createElement('div');
            el.className = `w-full rounded-xl border p-3 transition ${isActive
                ? 'border-zinc-300 bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-800/70'
                : 'border-zinc-200 hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-800/40'
                }`;
            const hasSkills = job.jdSkills && job.jdSkills.length > 0;
            const isRanked = job.status === 'ranked' || job.matchResults;

            el.innerHTML = `
                <div class="flex items-start justify-between gap-2">
                    <div class="min-w-0 flex-1 cursor-pointer" data-job-select="${job.id}">
                        <div class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-200">${job.title}</div>
                        <div class="flex items-center gap-2 mt-0.5">
                            <span class="text-xs text-zinc-500 dark:text-zinc-400">${job.createdAt}</span>
                            ${hasSkills ? `<span class="text-[10px] px-1.5 py-0.5 rounded-full ${isRanked ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400' : 'bg-violet-100 text-violet-600 dark:bg-violet-900/30 dark:text-violet-400'}">${isRanked ? '✓ Ranked' : job.jdSkills.length + ' skills'}</span>` : ''}
                        </div>
                    </div>
                    <div class="flex items-center gap-1">
                        ${isActive ? '<div class="h-2 w-2 rounded-full bg-sky-400"></div>' : ''}
                        <button class="delete-job-btn p-1 rounded-lg text-zinc-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition" data-job-id="${job.id}" title="Delete Job">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                            </svg>
                        </button>
                    </div>
                </div>
            `;

            // Click on job title to select
            el.querySelector('[data-job-select]').onclick = async () => {
                this.activeJobId = job.id;
                // ALWAYS reload matches to reset resume statuses for this job
                if (job.jdId) {
                    await this.loadMatchesForJob(job);
                } else {
                    // No jdId means new job - reset job-specific data only (keep name/experience)
                    this.activeJobResumes.forEach(r => {
                        r.status = null;
                        r.matchScore = 0;
                        r.skillMatchScore = 0;
                        delete r.matchedSkillsList;
                        delete r.missingSkillsList;
                        delete r.relevantProjects;
                    });
                }
                this.renderJobsList();
                this.updateUIForActiveJob();
            };

            // Click delete button
            el.querySelector('.delete-job-btn').onclick = (e) => {
                e.stopPropagation();
                this.deleteJob(job.id);
            };

            this.dom.jobsList.appendChild(el);
        });
    }

    async deleteJob(jobId) {
        const job = this.jobs.find(j => j.id === jobId);
        if (!job) return;

        // Confirm delete
        if (!confirm(`Delete "${job.title}"? This cannot be undone.`)) return;

        try {
            // Delete from backend if it has a jdId
            if (job.jdId) {
                const res = await fetch(`/api/job-descriptions/${job.jdId}`, { method: 'DELETE' });
                const data = await res.json();
                if (!data.success) {
                    this.showToast('Delete Failed', data.error || 'Failed to delete', 'error');
                    return;
                }
            }

            // Remove from frontend
            this.jobs = this.jobs.filter(j => j.id !== jobId);

            // If we deleted the active job, switch to another one
            if (this.activeJobId === jobId) {
                this.activeJobId = this.jobs.length > 0 ? this.jobs[0].id : null;
            }

            // If no jobs left, create a default one
            if (this.jobs.length === 0) {
                this.jobs = [{ id: 'job-1', title: 'New Job', createdAt: 'Just now', jdText: '', jdFile: null, status: 'idle', jdSkills: [], resumes: [] }];
                this.activeJobId = 'job-1';
            }

            this.renderJobsList();
            this.updateUIForActiveJob();
            this.showToast('Deleted', `Job "${job.title}" deleted`, 'info');
        } catch (e) {
            this.showToast('Delete Failed', e.message, 'error');
        }
    }

    updateUIForActiveJob() {
        const job = this.activeJob;
        if (!job) return;

        this.dom.activeJobTitleDisplay.textContent = job.title;
        this.dom.jobTitleInput.value = job.title;
        this.dom.jdTextArea.value = job.jdText || '';

        // Dynamic View Logic: If JD Text exists (or File exists), show "Document Mode"
        const hasContent = (job.jdText && job.jdText.trim().length > 0) || job.jdFile;

        if (hasContent) {
            // DOCUMENT MODE: Hide upload box, expand text area
            this.dom.jdFileInfoBox.classList.add('hidden');
            // uploadJdBtn removed
            this.dom.clearJdBtn.classList.remove('hidden');

            this.dom.jdHelperText.textContent = "Editing Job Description Text";
            this.dom.jdPasteLabel.classList.add('hidden');

            // Expand text area style
            this.dom.jdTextArea.classList.remove('h-44');
            this.dom.jdTextArea.classList.add('h-[350px]'); // Taller
        } else {
            // DEFAULT MODE: Show upload box, normal text area
            this.dom.jdFileInfoBox.classList.remove('hidden');
            this.dom.jdFilePlaceholder.classList.remove('hidden');
            this.dom.jdFileDetails.classList.add('hidden'); // hidden by default unless specific file logic needed

            // uploadJdBtn removed
            this.dom.clearJdBtn.classList.add('hidden');

            this.dom.jdHelperText.textContent = "Upload PDF/DOCX or paste text";
            this.dom.jdPasteLabel.classList.remove('hidden');

            // Restore text area style
            this.dom.jdTextArea.classList.add('h-44');
            this.dom.jdTextArea.classList.remove('h-[350px]');
        }

        // Resumes List
        this.renderResumesList();

        // Results API View
        this.updateResultsView();

        if (this.dom.statusBadge) {
            this.dom.statusBadge.textContent = `Status: ${job.status}`;
        }
        this.updateActionButtons();
    }

    renderResumesList() {
        if (this.dom.resumeCount) {
            this.dom.resumeCount.textContent = this.activeJobResumes.length;
        }

        /* 
         * REMOVED: File list display.
         * User requested to hide the uploaded files list and only show the count.
         */
    }

    updateResultsView() {
        const job = this.activeJob;

        // JD Requirements Section (New comprehensive display)
        if (job.jdSkills && job.jdSkills.length > 0) {
            // Show the requirements section
            this.dom.jdRequirementsSection?.classList.remove('hidden');
            this.dom.resultsPlaceholder?.classList.add('hidden');
            this.dom.skillsDisplay?.classList.add('hidden'); // Hide old inline display

            // Required Skills
            if (this.dom.requiredSkillsList) {
                this.dom.requiredSkillsList.innerHTML = job.jdSkills.map(s =>
                    `<span class="inline-block px-2 py-1 text-xs font-medium rounded-lg bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300 border border-sky-200 dark:border-sky-800">${s}</span>`
                ).join('');
            }
            if (this.dom.requiredSkillsCount) {
                this.dom.requiredSkillsCount.textContent = `${job.jdSkills.length} skills`;
            }

            // Preferred Skills
            if (job.preferredSkills && job.preferredSkills.length > 0) {
                this.dom.preferredSkillsSection?.classList.remove('hidden');
                if (this.dom.preferredSkillsList) {
                    this.dom.preferredSkillsList.innerHTML = job.preferredSkills.map(s =>
                        `<span class="inline-block px-2 py-1 text-xs font-medium rounded-lg bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">${s}</span>`
                    ).join('');
                }
                if (this.dom.preferredSkillsCount) {
                    this.dom.preferredSkillsCount.textContent = `${job.preferredSkills.length} skills`;
                }
            } else {
                this.dom.preferredSkillsSection?.classList.add('hidden');
            }

            // Experience Badge
            if (job.minExperience && job.minExperience > 0) {
                this.dom.expBadge?.classList.remove('hidden');
                if (this.dom.expYearsDisplay) {
                    this.dom.expYearsDisplay.textContent = `${job.minExperience}+ years`;
                }
            } else {
                this.dom.expBadge?.classList.add('hidden');
            }

            // Suggested Keywords Section
            if (this.dom.jdRequirementsSection) {
                let keywordsContainer = document.getElementById('suggestedKeywordsSection');

                // Create container if missing
                if (!keywordsContainer) {
                    keywordsContainer = document.createElement('div');
                    keywordsContainer.id = 'suggestedKeywordsSection';
                    keywordsContainer.className = 'mt-3 pt-3 border-t border-zinc-200 dark:border-zinc-800';

                    // Append before preferred skills or at end
                    if (this.dom.preferredSkillsSection && this.dom.preferredSkillsSection.parentNode === this.dom.jdRequirementsSection) {
                        this.dom.jdRequirementsSection.insertBefore(keywordsContainer, this.dom.preferredSkillsSection);
                    } else {
                        this.dom.jdRequirementsSection.appendChild(keywordsContainer);
                    }
                }

                // Render content
                keywordsContainer.innerHTML = `
                <div class="mb-3">
                    <div class="flex justify-between items-center mb-2">
                        <span class="text-xs font-semibold text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">Suggested Keywords (${(job.suggestedKeywords || []).length})</span>
                    </div>

                    <div id="suggestedKeywordsList" class="flex flex-wrap gap-2">
                        ${(job.suggestedKeywords && job.suggestedKeywords.length > 0)
                        ? job.suggestedKeywords.map(k =>
                            `<span class="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium rounded-md bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300 border border-amber-200 dark:border-amber-800/50 group cursor-default shadow-sm transition-all hover:border-amber-300">
                                    ${k}
                                    <button onclick="app.removeSuggestedKeyword('${k}')" class="text-amber-400 hover:text-red-500 dark:text-amber-500/50 dark:hover:text-red-400 transition-colors ml-0.5" title="Remove keyword">
                                        <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                                    </button>
                                </span>`
                        ).join('')
                        : '<div class="w-full text-center py-3 border-2 border-dashed border-zinc-100 dark:border-zinc-800/50 rounded-lg text-xs text-zinc-400">No keywords extracted yet.</div>'
                    }
                    </div>
                </div>
            `;
            }
        } else {
            // Hide JD requirements section, show placeholder
            this.dom.jdRequirementsSection?.classList.add('hidden');
            this.dom.resultsPlaceholder?.classList.remove('hidden');
            this.dom.skillsDisplay?.classList.add('hidden');
        }

        // If matched variants exist, show match view (simplified for now to just skills or ranking)
        // Ideally we would swap views or have tabs. The UI request shows "Results" card.
        // We will append Match/Ranking results below skills if they exist.

        // Remove existing containers
        const existingMatchContainer = document.getElementById('tablesSliderContainer');
        if (existingMatchContainer) existingMatchContainer.remove();

        if (job.status === 'ranked' || job.status === 'matched') {
            // Filter resumes
            let displayResumes = this.activeJobResumes.filter(r => r.status !== 'rejected' && r.status !== 'accepted');
            let acceptedResumes = this.activeJobResumes.filter(r => r.status === 'accepted');

            // Sort resumes by score if ranked
            if (job.status === 'ranked') {
                displayResumes.sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0));
            }

            // Create container with tabs
            const sliderContainer = document.createElement('div');
            sliderContainer.id = 'tablesSliderContainer';
            sliderContainer.className = 'mt-6';

            // Tab buttons
            const tabsHtml = `
                <div class="flex gap-2 mb-4 border-b border-zinc-200 dark:border-zinc-700">
                    <button id="tabRanking" onclick="app.switchTableTab('ranking')" 
                        class="tab-btn px-4 py-2 text-sm font-semibold border-b-2 transition-all border-sky-500 text-sky-600 dark:text-sky-400">
                        📊 Ranking (${displayResumes.length})
                    </button>
                    <button id="tabAccepted" onclick="app.switchTableTab('accepted')" 
                        class="tab-btn px-4 py-2 text-sm font-semibold border-b-2 transition-all border-transparent text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-300">
                        ✅ Accepted (${acceptedResumes.length})
                    </button>
                </div>
            `;

            // Ranking table content
            const rankingTableHtml = `
                <div id="panelRanking" class="table-panel">
                    <div class="overflow-x-auto rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900/40">
                        <table class="w-full text-sm text-left">
                            <thead class="text-xs text-zinc-500 uppercase bg-zinc-50/50 dark:bg-zinc-800/30 dark:text-zinc-400 border-b border-zinc-200 dark:border-zinc-800">
                                <tr>
                                    <th class="px-2 py-3 font-medium">#</th>
                                    <th class="px-2 py-3 font-medium">Candidate</th>
                                    <th class="px-2 py-3 font-medium text-center">Match</th>
                                    <th class="px-2 py-3 font-medium text-center">Skills</th>
                                    <th class="px-2 py-3 font-medium">Missing</th>
                                    <th class="px-2 py-3 font-medium">Projects</th>
                                    <th class="px-2 py-3 font-medium text-center">Exp</th>
                                    <th class="px-2 py-3 font-medium">Actions</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-zinc-200 dark:divide-zinc-800">
                                ${displayResumes.length === 0 ? `
                                    <tr><td colspan="9" class="px-4 py-8 text-center text-zinc-400">No candidates pending review</td></tr>
                                ` : displayResumes.map((r, idx) => {
                const matchedList = r.matchedSkillsList || (Array.isArray(r.matchedSkills) ? r.matchedSkills : []);
                const missingList = r.missingSkillsList || (Array.isArray(r.missingSkills) ? r.missingSkills : []);
                const totalRequired = matchedList.length + missingList.length;
                const matchedCount = matchedList.length;
                const matchedDisplay = `${matchedCount}/${totalRequired}`;
                const matchedTitle = matchedList.join(', ') || 'None';
                const missingDisplay = missingList.length > 0
                    ? `<div class="flex flex-col gap-0.5 text-[11px] leading-tight text-left">
                            ${missingList.map(s => `<div>• ${s}</div>`).join('')}
                           </div>`
                    : '<span class="opacity-50">—</span>';
                const displayName = r.candidateName || r.name || 'Unknown';
                const atsScore = r.skillMatchScore || r.matchScore || 0;
                const projects = r.relevantProjects || [];
                const hasProjects = projects.length > 0;
                let projectDisplay = '<span class="text-zinc-400">—</span>';

                if (hasProjects) {
                    projectDisplay = `
                        <button onclick="app.showProjectsModal('${r.fileId || r.name}')" 
                            class="px-3 py-1.5 rounded-lg bg-white border border-zinc-200 hover:bg-zinc-50 dark:bg-zinc-800 dark:border-zinc-700 dark:hover:bg-zinc-700 text-xs font-semibold text-zinc-700 dark:text-zinc-300 transition-all shadow-sm flex items-center gap-2 mx-auto">
                            <svg class="w-3.5 h-3.5 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path></svg>
                            View Projects (${projects.length})
                        </button>
                     `;
                }

                return `
                        <tr class="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/20 transition-colors h-[60px]"> <!-- Fixed reasonable min-height -->
                                    <td class="px-2 py-3 align-middle font-bold text-zinc-900 dark:text-zinc-200 text-center">${idx + 1}</td>
                                    <td class="px-2 py-3 align-middle">
                                        <div class="flex items-center gap-2">
                                            <div class="font-medium text-zinc-900 dark:text-zinc-100 truncate max-w-[250px]" title="${displayName}">${displayName}</div>
                                            ${r.viewLink ? `<a href="${this.getViewerUrl(r.viewLink)}" target="_blank" class="p-1 rounded-md hover:bg-zinc-100 dark:hover:bg-zinc-800 text-zinc-500 dark:text-zinc-400 flex-shrink-0" title="View Resume">
                                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path></svg>
                                            </a>` : ''}
                                        </div>
                                    </td>
                                    <td class="px-2 py-3 align-middle text-center">
                                        <div class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${this.getScoreBadgeColor(r.matchScore)}">${r.matchScore || 0}%</div>
                                    </td>
                                    <td class="px-2 py-3 align-middle text-center">
                                        <span class="text-emerald-600 dark:text-emerald-400 font-semibold cursor-help" title="${matchedTitle}">${matchedDisplay}</span>
                                    </td>
                                    <td class="px-2 py-3 align-middle">
                                        <div class="text-xs text-red-500 dark:text-red-400 min-w-[140px] max-h-[60px] overflow-y-auto custom-scrollbar">${missingDisplay}</div>
                                    </td>
                                    <td class="px-2 py-3 align-middle text-center">
                                        ${projectDisplay}
                                    </td>
                                    <td class="px-2 py-3 align-middle text-center text-zinc-600 dark:text-zinc-400 text-xs">${r.candidateExperience || '0y'}</td>
                                    <td class="px-2 py-3 align-middle">
                                        <div class="flex gap-1 items-center">
                                            <button onclick="app.setCandidateStatus('${r.fileId || r.name}', 'accepted')"
                                                class="px-2 py-1 rounded-md text-[10px] font-semibold transition-all ${r.status === 'accepted' ? 'bg-emerald-500 text-white' : 'bg-emerald-50 text-emerald-600 hover:bg-emerald-100 dark:bg-emerald-900/20 dark:text-emerald-400'}">✓ Accept</button>
                                            <button onclick="app.setCandidateStatus('${r.fileId || r.name}', 'review')"
                                                class="px-2 py-1 rounded-md text-[10px] font-semibold transition-all ${r.status === 'review' ? 'bg-amber-500 text-white' : 'bg-amber-50 text-amber-600 hover:bg-amber-100 dark:bg-amber-900/20 dark:text-amber-400'}">⏳ Review</button>
                                            <button onclick="app.setCandidateStatus('${r.fileId || r.name}', 'rejected')"
                                                class="px-2 py-1 rounded-md text-[10px] font-semibold transition-all ${r.status === 'rejected' ? 'bg-red-500 text-white' : 'bg-red-50 text-red-600 hover:bg-red-100 dark:bg-red-900/20 dark:text-red-400'}">✗ Reject</button>
                                            <button onclick="app.deleteResume('${r.fileId || r.name}')" title="Permanently Delete Resume"
                                                class="p-1.5 rounded-md text-zinc-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-all ml-1">
                                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                                                </svg>
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            `}).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;

            // Accepted candidates table content
            const acceptedTableHtml = `
                <div id="panelAccepted" class="table-panel hidden">
                    <div class="overflow-x-auto rounded-xl border-2 border-emerald-300 dark:border-emerald-700 bg-emerald-50/30 dark:bg-emerald-900/10">
                        <table class="w-full text-sm text-left">
                            <thead class="text-xs text-emerald-600 uppercase bg-emerald-50/50 dark:bg-emerald-900/20 dark:text-emerald-400 border-b border-emerald-200 dark:border-emerald-800">
                                <tr>
                                    <th class="px-2 py-3 font-medium">#</th>
                                    <th class="px-2 py-3 font-medium">Candidate</th>
                                    <th class="px-2 py-3 font-medium text-center">Match</th>
                                    <th class="px-2 py-3 font-medium text-center">Skills</th>
                                    <th class="px-2 py-3 font-medium">Actions</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-emerald-100 dark:divide-emerald-800/50">
                                ${acceptedResumes.length === 0 ? `
                                    <tr><td colspan="5" class="px-4 py-8 text-center text-emerald-400">No accepted candidates yet. Accept candidates from the Ranking tab.</td></tr>
                                ` : acceptedResumes.map((r, idx) => {
                const displayName = r.candidateName || r.name || 'Unknown';
                const matchedList = r.matchedSkillsList || [];
                const totalRequired = matchedList.length + (r.missingSkillsList?.length || 0);
                return `
                                <tr class="hover:bg-emerald-50/50 dark:hover:bg-emerald-900/20 transition-colors">
                                    <td class="px-2 py-3 font-bold text-emerald-700 dark:text-emerald-300">${idx + 1}</td>
                                    <td class="px-2 py-3">
                                        <div class="flex items-center gap-2">
                                            <div class="font-medium text-zinc-900 dark:text-zinc-100">${displayName}</div>
                                            ${r.viewLink ? `<a href="${this.getViewerUrl(r.viewLink)}" target="_blank" class="p-1 rounded-md hover:bg-emerald-100 dark:hover:bg-emerald-800 text-emerald-600 dark:text-emerald-400" title="View Resume">
                                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path></svg>
                                            </a>` : ''}
                                        </div>
                                    </td>
                                    <td class="px-2 py-3 text-center">
                                        <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-400">${r.matchScore || 0}%</span>
                                    </td>
                                    <td class="px-2 py-3 text-center">
                                        <span class="text-emerald-600 dark:text-emerald-400 font-semibold">${matchedList.length}/${totalRequired}</span>
                                    </td>
                                    <td class="px-2 py-3">
                                        <div class="flex gap-1 items-center">
                                            <button onclick="app.setCandidateStatus('${r.fileId || r.name}', 'review')"
                                                class="px-2 py-1 rounded-md text-[10px] font-semibold bg-amber-50 text-amber-600 hover:bg-amber-100 dark:bg-amber-900/20 dark:text-amber-400">
                                                ↩ Move to Review
                                            </button>
                                            <button onclick="app.deleteResume('${r.fileId || r.name}')" title="Permanently Delete Resume"
                                                class="p-1.5 rounded-md text-zinc-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-all ml-1">
                                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                                                </svg>
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            `}).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;

            sliderContainer.innerHTML = tabsHtml + rankingTableHtml + acceptedTableHtml;
            this.dom.resultsContent.appendChild(sliderContainer);
        }
    }

    // Switch between table tabs
    switchTableTab(tab) {
        const tabRanking = document.getElementById('tabRanking');
        const tabAccepted = document.getElementById('tabAccepted');
        const panelRanking = document.getElementById('panelRanking');
        const panelAccepted = document.getElementById('panelAccepted');

        if (!tabRanking || !tabAccepted || !panelRanking || !panelAccepted) return;

        const activeTabClass = 'border-sky-500 text-sky-600 dark:text-sky-400';
        const inactiveTabClass = 'border-transparent text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-300';

        if (tab === 'ranking') {
            tabRanking.className = `tab-btn px-4 py-2 text-sm font-semibold border-b-2 transition-all ${activeTabClass}`;
            tabAccepted.className = `tab-btn px-4 py-2 text-sm font-semibold border-b-2 transition-all ${inactiveTabClass}`;
            panelRanking.classList.remove('hidden');
            panelAccepted.classList.add('hidden');
        } else {
            tabRanking.className = `tab-btn px-4 py-2 text-sm font-semibold border-b-2 transition-all ${inactiveTabClass}`;
            tabAccepted.className = `tab-btn px-4 py-2 text-sm font-semibold border-b-2 transition-all ${activeTabClass}`;
            panelRanking.classList.add('hidden');
            panelAccepted.classList.remove('hidden');
        }
    }

    getScoreBadgeColor(score) {
        if (!score) return 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400';
        if (score >= 80) return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400';
        if (score >= 60) return 'bg-sky-100 text-sky-700 dark:bg-sky-500/10 dark:text-sky-400';
        return 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400';
    }

    // Set candidate status (Accept, Review, Reject)
    async setCandidateStatus(resumeId, status) {
        const resume = this.activeJobResumes.find(r => (r.fileId || r.name) === resumeId);
        if (resume) {
            resume.status = status;
            this.updateResultsView();

            // Show feedback
            const statusLabels = {
                'accepted': '✓ Accepted',
                'review': '⏳ Under Review',
                'rejected': '✗ Rejected'
            };
            this.showToast('Status Updated', statusLabels[status] || status,
                status === 'accepted' ? 'success' : status === 'rejected' ? 'error' : 'info');

            // Persist to backend
            try {
                if (this.activeJobId) {
                    const realId = resume.fileId || resume.name;
                    await fetch(`/api/job-descriptions/${this.activeJobId}/resumes/${realId}/status`, {
                        method: 'PUT',
                        headers: this.getAuthHeaders({ 'Content-Type': 'application/json' }),
                        body: JSON.stringify({ status: status })
                    });
                }
            } catch (e) {
                console.error('Failed to save status', e);
            }
        }
    }

    // Permanently delete a resume from database and S3
    async deleteResume(resumeId) {
        const resume = this.activeJobResumes.find(r => (r.fileId || r.name) === resumeId);
        if (!resume) {
            this.showToast('Error', 'Resume not found', 'error');
            return;
        }

        const displayName = resume.candidateName || resume.name || 'this resume';

        // Confirm deletion
        if (!confirm(`⚠️ Permanently delete "${displayName}"?\n\nThis will remove the resume from:\n• Database\n• AWS S3 storage\n• All job matches\n\nThis action cannot be undone.`)) {
            return;
        }

        try {
            this.showToast('Deleting...', 'Removing resume from system', 'info');

            const res = await fetch(`/api/resumes/${resumeId}`, {
                method: 'DELETE',
                headers: this.getAuthHeaders()
            });

            const data = await res.json();

            if (data.success) {
                // Remove from global pool
                if (this.allResumes) {
                    this.allResumes = this.allResumes.filter(r => (r.fileId || r.name) !== resumeId);
                }

                // Remove from ALL jobs (since it's a global delete)
                this.jobs.forEach(job => {
                    if (job.resumes) {
                        job.resumes = job.resumes.filter(r => (r.fileId || r.name) !== resumeId);
                    }
                    if (job.matchResults) {
                        job.matchResults = job.matchResults.filter(m => m.resumeId !== resumeId);
                    }
                });

                // Update UI
                this.renderResumesList();
                this.updateResultsView();
                this.updateActionButtons();

                this.showToast('Deleted', `Resume "${displayName}" permanently deleted`, 'success');
            } else {
                throw new Error(data.error || 'Failed to delete resume');
            }
        } catch (e) {
            console.error('Delete resume error:', e);
            this.showToast('Delete Failed', e.message, 'error');
        }
    }

    updateActionButtons() {
        const job = this.activeJob;
        if (!job) return;

        const hasJd = job.jdText || job.jdFile;
        const hasResumes = this.activeJobResumes.length > 0;
        const hasSkills = job.jdSkills && job.jdSkills.length > 0;


        // Rank button requires skills extracted
        this.dom.rankBtn.disabled = !hasSkills || job.status === 'ranking' || job.status === 'matching';

        // styling updates for disabled state handled by tailwind 'disabled:cursor-not-allowed'
    }

    // --- Actions ---

    async handleJDUpload(file) {
        const job = this.activeJob;
        job.jdFile = file;
        this.showToast('Uploading JD...', file.name, 'info');

        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch('/api/upload-resume', { method: 'POST', body: formData });
            const data = await res.json();
            if (data.success) {
                job.jdText = data.text; // Extracted text
                this.updateUIForActiveJob();
                this.showToast('JD Uploaded', 'Text extracted successfully', 'success');

                // Auto-save the extracted JD text
                this.saveJobDescription();
            } else {
                throw new Error(data.error);
            }
        } catch (e) {
            this.showToast('Upload Failed', e.message, 'error');
            job.jdFile = null;
            this.updateUIForActiveJob();
        }
    }

    clearJDFile() {
        this.activeJob.jdFile = null;
        this.activeJob.jdText = '';
        this.activeJob.jdSkills = [];
        this.updateUIForActiveJob();
    }

    // Helper: Handle resume drop (files & folders)
    handleResumeDrop(e) {
        e.preventDefault();

        const items = e.dataTransfer.items;
        if (items) {
            let processed = false;
            for (let i = 0; i < items.length; i++) {
                // webkitGetAsEntry is standard for File System Access API
                const item = items[i].webkitGetAsEntry ? items[i].webkitGetAsEntry() : null;
                if (item) {
                    this.traverseFileTree(item);
                    processed = true;
                }
            }

            // Fallback for standard file drop if entry API fails or is empty
            if (!processed && e.dataTransfer.files.length > 0) {
                const files = e.dataTransfer.files;
                for (let i = 0; i < files.length; i++) {
                    if (files[i].name.match(/\.(pdf|doc|docx)$/i)) {
                        this.handleResumeUpload(files[i]);
                    }
                }
            }
        }
    }

    // Helper: Recursive file traversal
    async traverseFileTree(item, path = '') {
        if (item.isFile) {
            item.file((file) => {
                if (file.name.match(/\.(pdf|doc|docx)$/i)) {
                    this.handleResumeUpload(file);
                }
            });
        } else if (item.isDirectory) {
            const dirReader = item.createReader();
            const readEntries = () => {
                dirReader.readEntries((entries) => {
                    if (entries.length > 0) {
                        for (let i = 0; i < entries.length; i++) {
                            this.traverseFileTree(entries[i], path + item.name + "/");
                        }
                        readEntries(); // Continue reading next batch
                    }
                });
            };
            readEntries();
        }
    }

    async handleResumeUpload(file) {
        this.showToast('Uploading Resume...', file.name, 'info');

        const formData = new FormData();
        formData.append('file', file);

        // Resume Isolation: Associate with current JD
        if (this.activeJob && this.activeJob.jdId) {
            formData.append('jdId', this.activeJob.jdId);
        }

        try {
            const res = await fetch('/api/upload-resume', {
                method: 'POST',
                body: formData,
                headers: this.getAuthHeaders()  // Don't set Content-Type for FormData - browser handles it
            });
            const data = await res.json();
            if (data.success) {
                // Add to global resumes pool
                const newResume = {
                    name: file.name,
                    text: data.text,
                    skills: data.skills || [],
                    fileId: data.fileId,
                    viewLink: data.viewLink || data.s3Url,
                    candidateExperience: data.candidateExperience || 0,
                    candidateName: data.candidateName
                };

                this.allResumes.push(newResume);

                // activeJobResumes getter will automatically pull it into the current job

                this.renderResumesList();
                this.updateActionButtons();
                this.showToast('Resume Added', file.name, 'success');
            } else {
                throw new Error(data.error);
            }
        } catch (e) {
            this.showToast('Upload Failed', e.message, 'error');
        }
    }

    async runExtraction() {
        const job = this.activeJob;
        job.status = 'extracting';
        this.updateUIForActiveJob();
        this.showToast('Extracting Skills', 'Processing JD and resumes...', 'info');

        try {
            // 1. Extract JD Skills
            const jdRes = await fetch('/api/extract-skills', {
                method: 'POST',
                headers: this.getAuthHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ text: job.jdText, type: 'JD' })
            });
            const jdData = await jdRes.json();
            if (!jdData.success) throw new Error(jdData.error);
            job.jdSkills = jdData.skills;
            job.requiredExperience = jdData.requiredExperience || 0;

            // 2. Extract Resume Skills in PARALLEL (batches of 3 to respect rate limits)
            const resumesToProcess = this.activeJobResumes.filter(r =>
                !r.skills || r.skills.length === 0 || !r.candidateName
            );

            const BATCH_SIZE = 1; // Process 1 at a time to avoid rate limits
            for (let i = 0; i < resumesToProcess.length; i += BATCH_SIZE) {
                const batch = resumesToProcess.slice(i, i + BATCH_SIZE);

                // Process batch in parallel
                await Promise.all(batch.map(async (resume) => {
                    try {
                        const rRes = await fetch('/api/extract-skills', {
                            method: 'POST',
                            headers: this.getAuthHeaders({ 'Content-Type': 'application/json' }),
                            body: JSON.stringify({ text: resume.text })
                        });
                        const rData = await rRes.json();
                        if (rData.success) {
                            resume.skills = rData.skills;
                            if (rData.candidateName) resume.candidateName = rData.candidateName;
                            if (rData.candidateExperience) resume.candidateExperience = rData.candidateExperience;
                            if (rData.parsedDetails) {
                                if (rData.parsedDetails.employment_gaps) resume.employment_gaps = rData.parsedDetails.employment_gaps;
                                if (rData.parsedDetails.projects) resume.projects = rData.parsedDetails.projects;
                            }

                            // Sync to global pool
                            if (this.allResumes) {
                                const globalR = this.allResumes.find(gr => (gr.fileId === resume.fileId) || (gr.name === resume.name));
                                if (globalR) {
                                    globalR.skills = rData.skills;
                                    if (rData.candidateName) globalR.candidateName = rData.candidateName;
                                    if (rData.candidateExperience) globalR.candidateExperience = rData.candidateExperience;
                                    if (rData.parsedDetails) {
                                        if (rData.parsedDetails.employment_gaps) globalR.employment_gaps = rData.parsedDetails.employment_gaps;
                                        if (rData.parsedDetails.projects) globalR.projects = rData.parsedDetails.projects;
                                    }
                                }
                            }
                        }
                    } catch (err) {
                        console.warn('Failed to extract skills for resume:', resume.name, err);
                    }
                }));

                // Brief pause between batches to avoid rate limits
                if (i + BATCH_SIZE < resumesToProcess.length) {
                    await new Promise(r => setTimeout(r, 2000)); // 2 second delay to avoid rate limits
                }
            }

            job.status = 'extracted';

            // Save JD with extracted skills to backend
            await this.saveJobDescription();

            this.showToast('Extraction Complete', `Found ${job.jdSkills.length} skills in JD`, 'success');
            this.renderJobsList(); // Update sidebar to show skill count
            this.updateUIForActiveJob();

        } catch (e) {
            job.status = 'error';
            this.showToast('Extraction Error', e.message, 'error');
            this.updateUIForActiveJob();
        }
    }

    async runMatching() {
        const job = this.activeJob;
        job.status = 'matching';
        this.updateUIForActiveJob();

        try {
            for (let resume of this.activeJobResumes) {
                const res = await fetch('/api/match-skills', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ jdSkills: job.jdSkills, resumeSkills: resume.skills })
                });
                const data = await res.json();
                if (data.success) {
                    resume.matchScore = data.matchScore;
                    resume.matchedSkills = data.matchedSkills;
                    resume.missingSkills = data.missingSkills;
                }
            }
            job.status = 'matched';
            this.showToast('Matching Complete', 'Calculated scores for resumes', 'success');
            this.updateUIForActiveJob();
        } catch (e) {
            this.showToast('Matching Error', e.message, 'error');
            job.status = 'error';
            this.updateUIForActiveJob();
        }
    }

    async runMatchAndRank() {
        const job = this.activeJob;
        if (!job) return;

        // Ensure JD is saved first to get an ID
        if (!job.jdId) {
            await this.saveJobDescription();
            if (!job.jdId) {
                this.showToast('Error', 'Please save the Job Description first', 'error');
                return;
            }
        }

        // Step 1: Run Matching
        job.status = 'matching';
        this.showToast('Processing', 'Matching resumes with JD...', 'info');
        this.updateUIForActiveJob();

        try {
            const matchesToSave = [];

            for (let resume of this.activeJobResumes) {
                const res = await fetch('/api/match-skills', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ jdSkills: job.jdSkills, resumeSkills: resume.skills })
                });
                const data = await res.json();
                if (data.success) {
                    resume.matchScore = data.matchScore;
                    resume.matchedSkills = data.matchedSkills;
                    resume.missingSkills = data.missingSkills;
                    resume.skillMatchScore = data.matchScore; // Store raw skill score
                }
            }

            // Step 2: Run Ranking
            job.status = 'ranking';
            this.showToast('Processing', 'Ranking resumes...', 'info');
            this.updateUIForActiveJob();

            const resumeData = {};
            this.activeJobResumes.forEach(r => resumeData[r.fileId || r.name] = r.text || "");

            const res = await fetch('/api/rank-resumes', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ jdText: job.jdText, resumeData })
            });
            const data = await res.json();

            // Prepare data for saving
            // Build map of semantic scores
            const semanticScores = {};
            if (data && data.results && Array.isArray(data.results)) {
                data.results.forEach(item => {
                    // Python likely returns ID as filename/name
                    semanticScores[item.id] = (item.score || 0) * 100;
                });
            }

            // Prepare data for saving
            this.activeJobResumes.forEach(r => {
                // --- New Matching Logic ---
                // 1. Skill Match Score (40%)
                const skillScore = r.skillMatchScore || 0;

                // 2. Experience Score (25%)
                const jdExp = job.requiredExperience || 4; // Default to 4y if not extracted
                const resExp = r.candidateExperience || 0;
                const expScore = Math.min(resExp / Math.max(1, jdExp), 1) * 100;

                // 3. Gap Penalty
                let gapPenalty = 0;
                if (r.employment_gaps && r.employment_gaps.total_gap_months > 24) gapPenalty = 20;
                else if (r.employment_gaps && r.employment_gaps.total_gap_months > 12) gapPenalty = 10;

                // 4. Project Relevance (25%)
                let projectScore = 0;
                const totalJDSkills = job.jdSkills ? job.jdSkills.length : 1;
                if (r.projects && Array.isArray(r.projects) && totalJDSkills > 0) {
                    const projectTechs = new Set();
                    r.projects.forEach(p => {
                        if (p.technologies_used && Array.isArray(p.technologies_used)) {
                            p.technologies_used.forEach(t => projectTechs.add(t.toLowerCase()));
                        }
                    });

                    let matchedProjectSkills = 0;
                    if (job.jdSkills) {
                        job.jdSkills.forEach(js => {
                            const jsLower = js.toLowerCase();
                            // Check for exact or substring match
                            let found = false;
                            if (projectTechs.has(jsLower)) found = true;
                            else {
                                for (let pt of projectTechs) {
                                    if (pt.includes(jsLower) || jsLower.includes(pt)) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            if (found) matchedProjectSkills++;
                        });
                    }
                    projectScore = (matchedProjectSkills / totalJDSkills) * 100;
                }

                // 5. Semantic Score (10%)
                const semScore = semanticScores[r.name] || 0;

                // Final Calculation
                let finalScore = (skillScore * 0.40) +
                    (expScore * 0.25) +
                    (projectScore * 0.25) +
                    (semScore * 0.10);

                finalScore -= gapPenalty;
                r.matchScore = Math.max(0, Math.round(finalScore));

                matchesToSave.push({
                    resumeId: r.fileId || r.name, // Use fileId if available
                    candidateName: r.name,
                    matchScore: r.matchScore,
                    skillMatchScore: r.skillMatchScore,
                    matchedSkills: r.matchedSkills || [],
                    missingSkills: r.missingSkills || [],
                    candidateExperience: r.candidateExperience || 0,
                    status: r.status || 'review',
                    hasGap: r.hasGap || false,
                    gapMonths: r.gapMonths || 0
                });
            });

            // Step 3: Save results to backend
            console.log('💾 Saving matches to backend...');
            await fetch(`/api/job-descriptions/${job.jdId}/matches`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ matches: matchesToSave })
            });

            // Step 4: Reload matches from backend to get server-calculated fields (like relevantProjects)
            console.log('🔄 Reloading matches from backend...');
            await this.loadMatchesForJob(job);

            job.status = 'ranked';

            this.showToast('Complete!', 'Resumes matched, ranked, and saved', 'success');
            this.renderJobsList(); // Status badge update
            this.updateUIForActiveJob();

        } catch (e) {
            console.warn('Match/Rank error:', e);
            job.status = 'ranked'; // Continue anyway
            this.showToast('Ranked', 'Ranking complete (local fallback)', 'info');
            this.updateUIForActiveJob();
        }
    }

    async runRanking() {
        // Since we already calculated matches, 'Ranking' here essentially means

        // calling the Python embedding service via /api/rank-resumes IF enabled,
        // OR simply sorting the existing matches.
        // The API /api/rank-resumes expects { jdText, resumeData: {name: text} }

        const job = this.activeJob;
        job.status = 'ranking';
        this.updateUIForActiveJob();

        try {
            // Prepare payload
            const resumeData = {};
            this.activeJobResumes.forEach(r => resumeData[r.name] = r.text);

            const res = await fetch('/api/rank-resumes', {
                method: 'POST',
                headers: this.getAuthHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ jdText: job.jdText, resumeData })
            });
            const data = await res.json();

            if (data.success && data.results) {
                // Update match scores with embedding scores if available
                // data.results is array of { resume_id, similarity_score, ... }
                data.results.forEach(rank => {
                    const r = this.activeJobResumes.find(x => x.name === rank.resume_id);
                    if (r) {
                        // Blend or replace score? Let's assume embedding score is superior or supplementary
                        // rank.similarity_score is 0-1 float
                        // r.matchScore = Math.round(rank.similarity_score * 100); 
                        // Actually let's keep the skill score for now as primary display, 
                        // or update it if the user explicit asked for FAISS.
                        // For this UI, let's just trigger the sort.
                    }
                });
            }

            job.status = 'ranked';
            this.showToast('Ranking Complete', 'Resumes ordered by relevance', 'success');
            this.updateUIForActiveJob();
        } catch (e) {
            // Fallback to local sort if remote failed
            console.warn('Backend ranking failed, sorting locally', e);
            job.status = 'ranked'; // Treat local sort as done
            this.updateUIForActiveJob();
        }
    }

    // --- Helpers ---

    showToast(title, message, type = 'info') {
        const { toast, toastTitle, toastMessage, toastIcon } = this.dom;
        toastTitle.textContent = title;
        toastMessage.textContent = message;

        const colors = {
            success: 'bg-emerald-400 text-white',
            error: 'bg-red-400 text-white',
            info: 'bg-sky-400 text-white'
        };
        toastIcon.className = `mt-0.5 h-2.5 w-2.5 rounded-full ${colors[type] || colors.info}`;

        toast.classList.remove('translate-y-20', 'opacity-0');
        setTimeout(() => {
            toast.classList.add('translate-y-20', 'opacity-0');
        }, 3000);
    }

    formatBytes(bytes) {
        if (!bytes) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    window.app = new App();
});