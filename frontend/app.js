/**
 * JD-Resume Matching Application (Vanilla JS + Tailwind)
 */

import { auth } from './firebase-config.js';
import { onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-auth.js";

class App {
    constructor() {
        // State
        this.jobs = [
            { id: 'job-1', title: 'Workday Recruiting Functional', createdAt: 'Today', jdText: '', jdFile: null, status: 'idle', results: null }
        ];
        this.activeJobId = 'job-1';
        this.theme = 'dark';

        // Global resumes - shared across all jobs
        this.allResumes = [];

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
            uploadJdBtn: document.getElementById('uploadJdBtn'),
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
            resumeFileInput: document.getElementById('resumeFileInput'),
            resumeFolderInput: document.getElementById('resumeFolderInput'),
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
            driveStatus: document.getElementById('driveStatus')
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

        // Auth Listener
        this.userData = null;
        onAuthStateChanged(auth, (user) => {
            if (user) {
                this.userData = user;
                this.updateUserProfileUI(user);
            } else {
                window.location.href = 'landing.html';
            }
        });

        // Initialize auto-save debounce timer
        this.saveJDTimeout = null;

        // Attach event listeners first
        this.attachEventListeners();

        // Load data from database
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

        // Now render the UI
        this.renderJobsList();
        this.updateUIForActiveJob();
    }

    // Load all saved JDs from database
    async loadSavedJD() {
        try {
            console.log('📥 Loading saved JDs...');
            const response = await fetch('/api/job-descriptions');
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
                        status: hasSkills ? 'extracted' : 'idle', // Set status based on skills
                        jdSkills: jd.requiredSkills || []
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
        if (!job || !job.jdId) return;

        try {
            const res = await fetch(`/api/job-descriptions/${job.jdId}/matches?limit=100`);
            const data = await res.json();

            if (data.success && data.matches && data.matches.length > 0) {
                console.log(`✅ Loaded ${data.matches.length} saved matches for ${job.title}`);

                // Store matches in the job
                job.matchResults = data.matches;
                job.status = 'ranked'; // Has saved results

                // Update allResumes with match data for display
                data.matches.forEach(match => {
                    // Find resume in allResumes by fileId or name
                    const resume = this.allResumes.find(r =>
                        r.fileId === match.resumeId ||
                        r.name === match.resumeName
                    );

                    if (resume) {
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

        // Only save if there's actual JD text
        if (!job.jdText || job.jdText.trim().length === 0) {
            console.log('⏭️  No JD text to save');
            return;
        }

        try {
            let response;

            if (job.jdId) {
                // UPDATE existing JD (PUT)
                console.log('📝 Updating existing JD:', job.jdId);
                response = await fetch(`/api/job-descriptions/${job.jdId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        title: job.title || 'Untitled JD',
                        requiredSkills: job.jdSkills || []
                    })
                });
            } else {
                // CREATE new JD (POST)
                console.log('💾 Creating new JD:', job.title);
                response = await fetch('/api/job-descriptions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        jdText: job.jdText,
                        title: job.title || 'Untitled JD'
                    })
                });
            }

            const data = await response.json();

            if (data.success) {
                // Store the jdId from backend (only if new)
                if (!job.jdId) {
                    job.jdId = data.jdId;
                }

                // Update skills from backend if we don't have them already
                if ((!job.jdSkills || job.jdSkills.length === 0) && data.requiredSkills) {
                    job.jdSkills = data.requiredSkills;
                }

                // Update status if we got skills
                if (job.jdSkills && job.jdSkills.length > 0) {
                    job.status = 'extracted';
                }

                console.log('✅ JD saved with', job.jdSkills?.length || 0, 'skills');
            }
        } catch (error) {
            console.error('Failed to save JD:', error);
        }
    }

    async loadSavedResumes() {
        try {
            // Using Spring Boot Backend Port 8080
            const res = await fetch('http://localhost:8080/api/resumes');
            const data = await res.json();

            if (data.success && data.resumes && data.resumes.length > 0) {
                // Load into global resumes array (shared across all jobs)
                data.resumes.forEach(r => {
                    // Avoid duplicates
                    if (!this.allResumes.find(existing => existing.fileId === r.fileId)) {
                        this.allResumes.push({
                            name: r.name,
                            candidateName: r.candidateName,
                            candidateExperience: r.candidateExperience,
                            text: r.text,
                            skills: r.skills || [],
                            matchScore: r.matchScore || 0,
                            fileId: r.fileId,
                            viewLink: r.viewLink
                        });
                    }
                });

                this.renderResumesList();
                this.updateActionButtons();
                this.showToast('Data Loaded', `Loaded ${data.resumes.length} resumes`, 'info');
            }
        } catch (e) {
            console.error("Failed to load saved resumes", e);
        }
    }

    updateUserProfileUI(user) {
        const profileEl = document.getElementById('userProfile');
        if (profileEl) {
            profileEl.innerHTML = `
                <div class="h-7 w-7 rounded-full bg-gradient-to-br from-sky-400 to-indigo-500 grid place-items-center text-[10px] text-white font-bold">
                    ${user.displayName ? user.displayName.charAt(0).toUpperCase() : (user.email ? user.email.charAt(0).toUpperCase() : 'U')}
                </div>
                <div class="leading-tight min-w-0">
                    <div class="text-xs font-semibold truncate max-w-[100px]">${user.displayName || 'User'}</div>
                    <div class="text-[11px] text-zinc-500 dark:text-zinc-400 truncate max-w-[100px]">${user.email}</div>
                </div>
                <button id="logoutBtn" class="ml-2 p-1 text-zinc-400 hover:text-red-500 transition" title="Sign out">
                   <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3 0l3-3m0 0l-3-3m3 3H9" />
                    </svg>
                </button>
            `;
            document.getElementById('logoutBtn').addEventListener('click', () => this.handleLogout());
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

    get activeJob() {
        return this.jobs.find(j => j.id === this.activeJobId);
    }

    attachEventListeners() {
        // Theme Toggle
        this.dom.themeToggleBtn.addEventListener('click', () => this.toggleTheme());

        // New Job
        this.dom.newJobBtn.addEventListener('click', () => this.createNewJob());


        // Job Title Rename with auto-save
        this.dom.jobTitleInput.addEventListener('input', (e) => {
            this.activeJob.title = e.target.value;
            this.dom.activeJobTitleDisplay.textContent = e.target.value || 'Job';
            this.renderJobsList();
            this.debouncedSaveJD();
        });

        // JD Text Input with auto-save
        this.dom.jdTextArea.addEventListener('input', (e) => {
            this.activeJob.jdText = e.target.value;
            this.updateActionButtons();
            this.debouncedSaveJD();
        });

        // JD File Upload
        this.dom.uploadJdBtn.addEventListener('click', () => this.dom.jdFileInput.click());
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

        // Drive Import
        this.dom.importDriveBtn.addEventListener('click', () => this.openDriveModal());
        this.dom.driveModalClose.addEventListener('click', () => this.closeDriveModal());
        this.dom.driveScanBtn.addEventListener('click', () => this.handleDriveImport());

        // API Actions
        this.dom.extractBtn.addEventListener('click', () => this.runExtraction());
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

            const res = await fetch('http://localhost:8080/api/import-drive', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
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

    createNewJob() {
        const id = 'job-' + Math.random().toString(16).slice(2, 8);
        const newJob = {
            id,
            title: 'New Job',
            createdAt: 'Just now',
            jdText: '',
            jdFile: null,
            status: 'idle',
            jdSkills: []
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
                // Load saved matches for this job
                if (job.jdId && !job.matchResults) {
                    await this.loadMatchesForJob(job);
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
                this.jobs = [{ id: 'job-1', title: 'New Job', createdAt: 'Just now', jdText: '', jdFile: null, status: 'idle', jdSkills: [] }];
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
            this.dom.uploadJdBtn.classList.add('hidden');
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

            this.dom.uploadJdBtn.classList.remove('hidden');
            this.dom.clearJdBtn.classList.add('hidden');

            this.dom.jdHelperText.textContent = "Drag & drop PDF/DOCX/txt, or click Upload";
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
        if (!this.dom.resumesList) return;

        this.dom.resumesList.innerHTML = '';
        if (this.dom.resumeCount) {
            this.dom.resumeCount.textContent = this.allResumes.length;
        }

        if (this.allResumes.length === 0) {
            this.dom.resumesList.innerHTML = '<div class="italic text-zinc-500 text-sm py-4 text-center">No resumes uploaded yet.</div>';
            return;
        }

        this.allResumes.forEach(resume => {
            const div = document.createElement('div');
            div.className = 'flex items-center justify-between p-2 rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900/50';

            // Link to view if available
            let nameHtml = `<span class="truncate max-w-[150px] text-zinc-700 dark:text-zinc-100 text-sm">${resume.name}</span>`;
            if (resume.viewLink) {
                nameHtml = `<a href="${resume.viewLink}" target="_blank" class="truncate max-w-[150px] text-sky-600 hover:underline dark:text-sky-400 text-sm">${resume.name}</a>`;
            }

            div.innerHTML = `
                ${nameHtml}
                <span class="text-[10px] bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400 px-1.5 py-0.5 rounded">${resume.skills ? resume.skills.length + ' skills' : 'Processing...'}</span>
            `;
            this.dom.resumesList.appendChild(div);
        });
    }

    updateResultsView() {
        const job = this.activeJob;

        // Extracted Skills
        if (job.jdSkills && job.jdSkills.length > 0) {
            this.dom.resultsPlaceholder.classList.add('hidden');
            this.dom.skillsDisplay.classList.remove('hidden');
            this.dom.skillsList.innerHTML = job.jdSkills.map(s =>
                `<span class="skill-tag">${s}</span>`
            ).join('');
        } else {
            this.dom.resultsPlaceholder.classList.remove('hidden');
            this.dom.skillsDisplay.classList.add('hidden');
        }

        // If matched variants exist, show match view (simplified for now to just skills or ranking)
        // Ideally we would swap views or have tabs. The UI request shows "Results" card.
        // We will append Match/Ranking results below skills if they exist.

        const existingMatchContainer = document.getElementById('matchResultsContainer');
        if (existingMatchContainer) existingMatchContainer.remove();

        if (job.status === 'ranked' || job.status === 'matched') {
            const matchDiv = document.createElement('div');
            matchDiv.id = 'matchResultsContainer';
            matchDiv.className = 'mt-6 overflow-x-auto rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900/40';

            // Sort resumes by score if ranked
            const displayResumes = [...this.allResumes];
            if (job.status === 'ranked') {
                displayResumes.sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0));
            }

            matchDiv.innerHTML = `
                <table class="w-full text-sm text-left">
                    <thead class="text-xs text-zinc-500 uppercase bg-zinc-50/50 dark:bg-zinc-800/30 dark:text-zinc-400 border-b border-zinc-200 dark:border-zinc-800">
                        <tr>
                            <th class="px-2 py-3 font-medium">#</th>
                            <th class="px-2 py-3 font-medium">Candidate</th>
                            <th class="px-2 py-3 font-medium text-center">Match</th>
                            <th class="px-2 py-3 font-medium text-center">ATS</th>
                            <th class="px-2 py-3 font-medium text-center">Skills</th>
                            <th class="px-2 py-3 font-medium">Missing</th>
                            <th class="px-2 py-3 font-medium">Projects</th>
                            <th class="px-2 py-3 font-medium text-center">Exp</th>
                            <th class="px-2 py-3 font-medium text-center">Gaps</th>
                            <th class="px-2 py-3 font-medium">Actions</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-zinc-200 dark:divide-zinc-800">
                        ${displayResumes.map((r, idx) => {
                // Get skill counts
                const matchedList = r.matchedSkillsList || (Array.isArray(r.matchedSkills) ? r.matchedSkills : []);
                const missingList = r.missingSkillsList || (Array.isArray(r.missingSkills) ? r.missingSkills : []);
                const totalRequired = matchedList.length + missingList.length;
                const matchedCount = matchedList.length;

                // Format matched as X/Y
                const matchedDisplay = `${matchedCount}/${totalRequired}`;
                const matchedTitle = matchedList.join(', ') || 'None';

                // Format missing skills - show first 2, tooltip shows all
                const missingDisplay = missingList.length > 0
                    ? (missingList.length <= 2
                        ? missingList.join(', ')
                        : missingList.slice(0, 2).join(', ') + ` +${missingList.length - 2}`)
                    : '—';
                const missingTitle = missingList.join(', ') || 'None missing';

                // Use candidate name if available
                const displayName = r.candidateName || r.name || 'Unknown';

                // ATS Score (skill match score specifically)
                const atsScore = r.skillMatchScore || r.matchScore || 0;

                // Relevant Projects
                const projects = r.relevantProjects || [];
                const hasProjects = projects.length > 0;
                const projectNames = projects.map(p => p.name).join(', ');
                const projectTooltip = projects.map(p => `${p.name} (${p.matchingTechs?.join(', ') || ''})`).join('\n') || 'No relevant projects';
                const projectDisplay = hasProjects
                    ? (projects.length <= 2
                        ? projectNames
                        : projects.slice(0, 2).map(p => p.name).join(', ') + ` +${projects.length - 2}`)
                    : '—';

                return `
                            <tr class="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/20 transition-colors">
                                <td class="px-2 py-3 font-bold text-zinc-900 dark:text-zinc-200">
                                    ${idx + 1}
                                </td>
                                <td class="px-2 py-3">
                                    <div class="flex items-center gap-2">
                                        <div>
                                            <div class="font-medium text-zinc-900 dark:text-zinc-100 truncate max-w-[250px]" title="${displayName}">${displayName}</div>
                                            <div class="text-[10px] text-zinc-500 truncate max-w-[250px]">${r.resumeName || r.name}</div>
                                        </div>
                                        ${r.viewLink
                        ? `<a href="${r.viewLink}" target="_blank" class="p-1 rounded-md hover:bg-zinc-100 dark:hover:bg-zinc-800 text-zinc-500 dark:text-zinc-400 flex-shrink-0" title="View Resume">
                                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
                                                </svg>
                                              </a>`
                        : ''}
                                    </div>
                                </td>
                                <td class="px-2 py-3 text-center">
                                    <div class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${this.getScoreBadgeColor(r.matchScore)}">
                                        ${r.matchScore || 0}%
                                    </div>
                                </td>
                                <td class="px-2 py-3 text-center">
                                    <span class="text-xs font-medium text-sky-600 dark:text-sky-400">${atsScore}%</span>
                                </td>
                                <td class="px-2 py-3 text-center">
                                    <span class="text-emerald-600 dark:text-emerald-400 font-semibold cursor-help" title="${matchedTitle}">${matchedDisplay}</span>
                                </td>
                                <td class="px-2 py-3">
                                    <div class="text-xs text-red-500 dark:text-red-400 truncate max-w-[120px] cursor-help" title="${missingTitle}">
                                        ${missingDisplay}
                                    </div>
                                </td>
                                <td class="px-2 py-3">
                                    ${hasProjects
                        ? `<div class="flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400 truncate max-w-[130px] cursor-help" title="${projectTooltip}">
                                            <svg class="w-3 h-3 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"></path></svg>
                                            <span class="truncate">${projectDisplay}</span>
                                          </div>`
                        : '<span class="text-zinc-400 text-xs">—</span>'}
                                </td>
                                <td class="px-2 py-3 text-center text-zinc-600 dark:text-zinc-400 text-xs">
                                    ${r.candidateExperience || '0y'}
                                </td>
                                <td class="px-2 py-3 text-center">
                                    ${r.hasGap
                        ? `<span class="text-amber-500 text-xs font-medium" title="${r.gapMonths} months gap">Yes</span>`
                        : '<span class="text-zinc-400 text-xs">—</span>'}
                                </td>
                                <td class="px-2 py-3">
                                    <div class="flex items-center gap-1">
                                        <button onclick="app.setCandidateStatus('${r.fileId || r.name}', 'accepted')" 
                                            class="candidate-action-btn px-2 py-1 rounded-md text-[10px] font-semibold transition-all
                                            ${r.status === 'accepted'
                        ? 'bg-emerald-500 text-white shadow-sm'
                        : 'bg-emerald-50 text-emerald-600 hover:bg-emerald-100 dark:bg-emerald-900/20 dark:text-emerald-400 dark:hover:bg-emerald-900/40'}">
                                            ✓ Accept
                                        </button>
                                        <button onclick="app.setCandidateStatus('${r.fileId || r.name}', 'review')"
                                            class="candidate-action-btn px-2 py-1 rounded-md text-[10px] font-semibold transition-all
                                            ${r.status === 'review'
                        ? 'bg-amber-500 text-white shadow-sm'
                        : 'bg-amber-50 text-amber-600 hover:bg-amber-100 dark:bg-amber-900/20 dark:text-amber-400 dark:hover:bg-amber-900/40'}">
                                            ⏳ Review
                                        </button>
                                        <button onclick="app.setCandidateStatus('${r.fileId || r.name}', 'rejected')"
                                            class="candidate-action-btn px-2 py-1 rounded-md text-[10px] font-semibold transition-all
                                            ${r.status === 'rejected'
                        ? 'bg-red-500 text-white shadow-sm'
                        : 'bg-red-50 text-red-600 hover:bg-red-100 dark:bg-red-900/20 dark:text-red-400 dark:hover:bg-red-900/40'}">
                                            ✗ Reject
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        `}).join('')}
                    </tbody>
                </table>
            `;


            this.dom.resultsContent.appendChild(matchDiv);
        }
    }

    getScoreBadgeColor(score) {
        if (!score) return 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400';
        if (score >= 80) return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400';
        if (score >= 60) return 'bg-sky-100 text-sky-700 dark:bg-sky-500/10 dark:text-sky-400';
        return 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400';
    }

    // Set candidate status (Accept, Review, Reject)
    setCandidateStatus(resumeId, status) {
        const resume = this.allResumes.find(r => (r.fileId || r.name) === resumeId);
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
        }
    }

    updateActionButtons() {
        const job = this.activeJob;
        if (!job) return;

        const hasJd = job.jdText || job.jdFile;
        const hasResumes = this.allResumes.length > 0;
        const hasSkills = job.jdSkills && job.jdSkills.length > 0;

        this.dom.extractBtn.disabled = !(hasJd && hasResumes) || job.status === 'extracting';
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

    async handleResumeUpload(file) {
        this.showToast('Uploading Resume...', file.name, 'info');

        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch('/api/upload-resume', { method: 'POST', body: formData });
            const data = await res.json();
            if (data.success) {
                // Add to global resumes (available for all jobs)
                this.allResumes.push({
                    name: file.name,
                    candidateName: data.candidateName,
                    text: data.text,
                    skills: data.skills || [],
                    matchScore: 0,
                    fileId: data.fileId,
                    viewLink: data.viewLink || data.s3Url
                });
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
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text: job.jdText })
            });
            const jdData = await jdRes.json();
            if (!jdData.success) throw new Error(jdData.error);
            job.jdSkills = jdData.skills;

            // 2. Extract Resume Skills (for each)
            // Parallel execution might be faster but serial safer for rate limits
            for (let resume of this.allResumes) {
                // Skip if already has skills AND candidate name
                if (resume.skills && resume.skills.length > 0 && resume.candidateName) continue;

                const rRes = await fetch('/api/extract-skills', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ text: resume.text })
                });
                const rData = await rRes.json();
                if (rData.success) {
                    resume.skills = rData.skills;
                    // Capture candidate details if available
                    if (rData.candidateName) resume.candidateName = rData.candidateName;
                    if (rData.candidateExperience) resume.candidateExperience = rData.candidateExperience;
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
            for (let resume of this.allResumes) {
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

        // Trigger Backend Semantic Matching (Vector Database Ranking)
        job.status = 'ranking';
        this.showToast('Processing', 'Calculating vector similarity...', 'info');
        this.updateUIForActiveJob();

        try {
            // Trigger matching on backend
            // This now uses 100% Vector Embedding weight as configured in backend
            const matchRes = await fetch(`/api/job-descriptions/${job.jdId}/match`, {
                method: 'POST'
            });
            const matchData = await matchRes.json();

            if (!matchData.success) throw new Error(matchData.error);

            // Fetch the calculated results
            await this.loadMatchesForJob(job);

            job.status = 'ranked';
            this.showToast('Complete!', 'Resumes ranked by Vector Similarity', 'success');
            this.renderJobsList(); // Status badge update
            this.updateUIForActiveJob();

        } catch (e) {
            console.warn('Match/Rank error:', e);
            job.status = 'error';
            this.showToast('Ranking Error', e.message, 'error');
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
            this.allResumes.forEach(r => resumeData[r.name] = r.text);

            const res = await fetch('/api/rank-resumes', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ jdText: job.jdText, resumeData })
            });
            const data = await res.json();

            if (data.success && data.results) {
                // Update match scores with embedding scores if available
                // data.results is array of { resume_id, similarity_score, ... }
                data.results.forEach(rank => {
                    const r = this.allResumes.find(x => x.name === rank.resume_id);
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