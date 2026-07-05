document.addEventListener('DOMContentLoaded', () => {
    // Android app styling overrides removed.
    // The APK will use the same Web UI exactly.

    const imageInput    = document.getElementById('image-input');
    const attachBtn     = document.getElementById('browse-btn');
    const imgPlaceholder= document.getElementById('drop-zone');
    const imgPreviewWrap= document.getElementById('img-view');
    const imagePreview  = document.getElementById('image-preview');
    const removeImgBtn  = document.getElementById('change-btn');
    const questionInput = document.getElementById('question-input');
    const askBtn        = document.getElementById('ask-btn');
    const chips         = document.querySelectorAll('.chip');

    const loadingState  = document.getElementById('loading-state');
    const resultsState  = document.getElementById('results-state');
    const errorState    = document.getElementById('error-state');
    const errorText     = document.getElementById('error-text');

    const topAnswerEl   = document.getElementById('top-answer-text');
    const confValueEl   = document.getElementById('confidence-value');
    const otherListEl   = document.getElementById('other-list');
    
    const geminiGreeting = document.querySelector('.gemini-greeting');

    let selectedFile = null;
    let currentPreviewUrl = null;

    // ---- Helpers ----
    function showState(el) {
        [loadingState, resultsState, errorState].forEach(s => s && s.classList.add('hidden'));
        if (el) el.classList.remove('hidden');
        
        // Hide header and chips when loading or showing results
        if (el === loadingState || el === resultsState) {
            const rightHeader = document.querySelector('.right-header');
            const chipsContainer = document.querySelector('.chips');
            if (rightHeader) rightHeader.style.display = 'none';
            if (chipsContainer) chipsContainer.style.display = 'none';
            if (geminiGreeting) geminiGreeting.style.display = 'none';
        }
    }

    function syncBtn() {
        askBtn.disabled = !(selectedFile && questionInput.value.trim());
    }

    function esc(str) {
        const d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }

    // ---- Image attachment ----
    attachBtn.addEventListener('click', () => {
        imageInput.removeAttribute('capture');
        imageInput.click();
    });

    imageInput.addEventListener('change', e => {
        const file = e.target.files[0];
        if (file) loadFile(file);
    });

    removeImgBtn.addEventListener('click', () => {
        selectedFile = null;
        imageInput.value = '';
        if (currentPreviewUrl) {
            URL.revokeObjectURL(currentPreviewUrl);
            currentPreviewUrl = null;
        }
        imagePreview.src = '';
        imgPreviewWrap.classList.add('hidden');
        imgPlaceholder.classList.remove('hidden');
        
        const rightHeader = document.querySelector('.right-header');
        const chipsContainer = document.querySelector('.chips');
        if (rightHeader) rightHeader.style.display = '';
        if (chipsContainer) chipsContainer.style.display = '';
        
        showState(null);
        
        if (geminiGreeting && document.body.classList.contains('android-app')) {
            geminiGreeting.style.display = 'flex';
        }
        syncBtn();
    });

    // Drag & drop on the drop zone
    const dropZone = document.getElementById('drop-zone');
    dropZone.addEventListener('dragover', e => { e.preventDefault(); dropZone.style.borderColor = 'rgba(167,139,250,0.5)'; });
    dropZone.addEventListener('dragleave', () => dropZone.style.borderColor = '');
    dropZone.addEventListener('drop', e => {
        e.preventDefault();
        dropZone.style.borderColor = '';
        const file = e.dataTransfer.files[0];
        if (file && file.type.startsWith('image/')) loadFile(file);
    });

    function loadFile(file) {
        if (!file.type.startsWith('image/')) return;
        selectedFile = file;
        
        // Revoke old URL to prevent memory leaks
        if (currentPreviewUrl) {
            URL.revokeObjectURL(currentPreviewUrl);
        }
        
        currentPreviewUrl = URL.createObjectURL(file);
        imagePreview.src = currentPreviewUrl;
        imgPlaceholder.classList.add('hidden');
        imgPreviewWrap.classList.remove('hidden');
        if (geminiGreeting && document.body.classList.contains('android-app')) {
            geminiGreeting.style.display = 'none';
        }
        syncBtn();
    }

    // ---- Question input ----
    questionInput.addEventListener('input', syncBtn);
    questionInput.addEventListener('keydown', e => {
        if (e.key === 'Enter' && !askBtn.disabled) askBtn.click();
    });

    // ---- Chips ----
    chips.forEach(chip => {
        chip.addEventListener('click', () => {
            questionInput.value = chip.dataset.q;
            syncBtn();
            questionInput.focus();
        });
    });

    // ---- Speech Recognition ----
    const micBtn = document.getElementById('mic-btn');
    
    // Exposed for Android Native Speech
    window.onAndroidSpeechResult = function(text) {
        questionInput.value = text;
        syncBtn();
        if (!askBtn.disabled) askBtn.click();
    };

    if (micBtn) {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        
        micBtn.addEventListener('click', () => {
            if (window.AndroidApp) {
                // Use Android Native
                window.AndroidApp.startSpeechRecognition();
                return;
            }
            
            if (SpeechRecognition) {
                if (micBtn.classList.contains('listening')) {
                    if (window.activeRecognition) window.activeRecognition.stop();
                } else {
                    const recognition = new SpeechRecognition();
                    window.activeRecognition = recognition;
                    recognition.continuous = false;
                    recognition.interimResults = false;

                    recognition.onstart = () => {
                        micBtn.classList.add('listening');
                        micBtn.textContent = '🛑';
                        questionInput.placeholder = 'Listening...';
                    };

                    recognition.onresult = (e) => {
                        let finalTrans = '';
                        for (let i = e.resultIndex; i < e.results.length; ++i) {
                            if (e.results[i].isFinal) {
                                finalTrans += e.results[i][0].transcript;
                            }
                        }
                        if (finalTrans) {
                            questionInput.value = finalTrans;
                            syncBtn();
                            if (!askBtn.disabled) askBtn.click();
                        }
                    };

                    recognition.onerror = (e) => {
                        console.error('Speech recognition error', e);
                        questionInput.placeholder = 'What is in this image?';
                    };

                    recognition.onend = () => {
                        micBtn.classList.remove('listening');
                        micBtn.textContent = '🎙️';
                        questionInput.placeholder = 'What is in this image?';
                    };

                    recognition.start();
                }
            }
        });
        
        if (!window.AndroidApp && !SpeechRecognition) {
            micBtn.style.display = 'none'; // Not supported
        }
    }

        // ---- TTS Toggle ----
        let isTtsEnabled = true;
        const ttsToggleBtn = document.getElementById('tts-toggle-btn');
        if (ttsToggleBtn) {
            ttsToggleBtn.addEventListener('click', () => {
                isTtsEnabled = !isTtsEnabled;
                ttsToggleBtn.textContent = isTtsEnabled ? '🔊' : '🔇';
                ttsToggleBtn.title = isTtsEnabled ? 'Disable Read out loud' : 'Enable Read out loud';
                if (!isTtsEnabled && 'speechSynthesis' in window) {
                    window.speechSynthesis.cancel();
                }
            });
        }
    
        // ---- Ask AI ----
    askBtn.addEventListener('click', async () => {
        if (!selectedFile || !questionInput.value.trim()) return;

        const question = questionInput.value.trim();
        showState(loadingState);
        askBtn.disabled = true;

        // Determine intent automatically
        const lower = question.toLowerCase();
        let currentMode = 'vision';
        if (lower.includes('read') || lower.includes('text') || lower.includes('sign') || lower.includes('menu') || lower.includes('document') || lower.includes('ocr') || lower.includes('letter') || lower.includes('word')) {
            currentMode = 'ocr';
        } else if (lower.includes('guide') || lower.includes('navigate') || lower.includes('walk') || lower.includes('path') || lower.includes('direction')) {
            currentMode = 'navigate';
        }

        try {
            const form = new FormData();
            form.append('image', selectedFile);
            if (!window.currentSessionId) {
                window.currentSessionId = Math.random().toString(36).substring(2);
            }
            if (currentMode === 'vision') {
                form.append('question', question);
                form.append('session_id', window.currentSessionId);
            }

            let endpoint = '/predict';
            if (currentMode === 'ocr') endpoint = '/ocr';
            if (currentMode === 'navigate') endpoint = '/navigate';

            let apiUrl = '/api' + endpoint;
            if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
                apiUrl = 'http://localhost:8000' + endpoint;
            } else if (navigator.userAgent.includes("VQA-Android-App")) {
                apiUrl = 'https://avinavpri-vqa-backend.hf.space' + endpoint;
            }

            const res = await fetch(apiUrl, { method: 'POST', body: form });

            if (!res.ok) {
                const err = await res.json().catch(() => null);
                throw new Error(err?.detail || `Server error ${res.status}`);
            }

            const data = await res.json();
            // Pass the mode to the renderer
            renderResults(data, currentMode);
        } catch (err) {
            console.error(err);
            errorText.textContent = err.message || 'Could not connect to the backend.';
            showState(errorState);
        } finally {
            syncBtn();
        }
    });

    // ---- Render Results ----
    function renderResults(data, currentMode) {
        if (currentMode === 'vision') {
            const preds = data.predictions;
            if (!preds || !preds.length) {
                errorText.textContent = 'No predictions returned from the model.';
                showState(errorState);
                return;
            }

            const top = preds[0];
            const topPct = (top.confidence * 100).toFixed(1);

            topAnswerEl.textContent = top.answer;
            confValueEl.textContent = topPct + '%';

            // Read out loud
            if (isTtsEnabled && 'speechSynthesis' in window) {
                window.speechSynthesis.cancel(); 
                window.currentUtterance = new SpeechSynthesisUtterance(`The best answer is ${top.answer}`);
                window.speechSynthesis.speak(window.currentUtterance);
            }

            // Others
            otherListEl.innerHTML = '';
            preds.slice(1).forEach((pred, i) => {
                const pct = (pred.confidence * 100).toFixed(1);
                const row = document.createElement('div');
                row.className = 'other-row';
                row.innerHTML = `
                    <div class="other-bar" style="width:0%"></div>
                    <span class="other-name">${esc(pred.answer)}</span>
                    <span class="other-pct">${pct}%</span>
                `;
                otherListEl.appendChild(row);
                setTimeout(() => row.querySelector('.other-bar').style.width = pct + '%', 60 + i * 60);
            });
            document.querySelector('.others-block').style.display = 'block';
            document.querySelector('.result-model').textContent = '⚡ dandelin/vilt-b32-finetuned-vqa';

        } else if (currentMode === 'ocr') {
            const text = data.text || 'No text found in the image.';
            topAnswerEl.textContent = text;
            confValueEl.textContent = '';
            otherListEl.innerHTML = '';
            document.querySelector('.others-block').style.display = 'none';
            document.querySelector('.result-model').textContent = '⚡ EasyOCR Extract';
            
            if (isTtsEnabled && 'speechSynthesis' in window) {
                window.speechSynthesis.cancel();
                window.currentUtterance = new SpeechSynthesisUtterance(text);
                window.speechSynthesis.speak(window.currentUtterance);
            }
        } else if (currentMode === 'navigate') {
            let msg = 'Path is clear.';
            if (data.detections && data.detections.length > 0) {
                msg = `${data.detections[0].object} ahead`;
            }
            topAnswerEl.textContent = msg;
            confValueEl.textContent = '';
            otherListEl.innerHTML = '';
            document.querySelector('.others-block').style.display = 'none';
            document.querySelector('.result-model').textContent = '⚡ YOLOv8 Navigation';

            if (isTtsEnabled && 'speechSynthesis' in window) {
                window.speechSynthesis.cancel();
                window.currentUtterance = new SpeechSynthesisUtterance(msg);
                window.speechSynthesis.speak(window.currentUtterance);
            }
        }

        showState(resultsState);
        // Smooth scroll to results
        resultsState.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    // ---- Hamburger Menu ----
    const hamburgerBtn = document.getElementById('hamburger-btn');
    const navLinks     = document.getElementById('nav-links');

    hamburgerBtn.addEventListener('click', () => {
        hamburgerBtn.classList.toggle('open');
        navLinks.classList.toggle('open');
    });

    // Close menu when a nav link is clicked
    navLinks.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', () => {
            hamburgerBtn.classList.remove('open');
            navLinks.classList.remove('open');
        });
    });

    // ---- About Modal ----
    const aboutModal   = document.getElementById('about-modal');
    const aboutBtn     = document.getElementById('nav-about');
    const closeModalBtn= document.getElementById('modal-close-btn');

    aboutBtn.addEventListener('click', (e) => {
        e.preventDefault();
        aboutModal.classList.remove('hidden');
    });

    closeModalBtn.addEventListener('click', () => {
        aboutModal.classList.add('hidden');
    });

    // Close modal on overlay click
    aboutModal.addEventListener('click', (e) => {
        if (e.target === aboutModal) aboutModal.classList.add('hidden');
    });

    // Close modal on Escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !aboutModal.classList.contains('hidden')) {
            aboutModal.classList.add('hidden');
        }
    });

    // ---- Camera Button ----
    const cameraBtn = document.getElementById('camera-btn');
    if (cameraBtn) {
        cameraBtn.addEventListener('click', () => {
            // On mobile this opens camera; on desktop it opens file picker
            imageInput.setAttribute('capture', 'environment');
            imageInput.click();
        });
    }
    // ---- Navbar Scroll Effect ----
    const navbar = document.getElementById('navbar');
    window.addEventListener('scroll', () => {
        navbar.classList.toggle('scrolled', window.scrollY > 10);
    });

});
