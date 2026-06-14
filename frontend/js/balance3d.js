class Balance3D {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        this.scene = null;
        this.camera = null;
        this.renderer = null;
        this.balanceGroup = null;
        this.highDetailGroup = null;
        this.lowDetailGroup = null;
        this.lod = null;

        this.beam = null;
        this.knives = [];
        this.pans = [];

        this.autoRotate = false;
        this.raycaster = new THREE.Raycaster();
        this.mouse = new THREE.Vector2();
        this.clickableObjects = [];
        this.onBalanceClick = null;
        this.balanceData = null;

        this.isMobile = this._detectMobile();
        this.performanceLevel = this._detectPerformanceLevel();
        this.targetPixelRatio = this._getTargetPixelRatio();
        this.useLowDetail = this.isMobile && this.performanceLevel < 2;

        this.animationId = null;
        this.time = 0;
        this.lastFrameTime = 0;
        this.frameDeltaSmoothing = 0;
        this.dynamicFpsThrottle = 1;
        this.frameSkipCounter = 0;

        this.physicsEnabled = !this.useLowDetail;
        this.physicsStep = this.useLowDetail
            ? AppConfig.balance3d.physics.fixedStepLow
            : AppConfig.balance3d.physics.fixedStepHigh;
        this.physicsAccumulator = 0;

        this.swingAngle = 0;
        this.swingVelocity = 0;

        this._touchState = {
            isPinching: false,
            initialPinchDistance: 0,
            initialSphericalRadius: 0,
            lastTouchX: 0,
            lastTouchY: 0
        };

        this.init();
        this.animate();
        this._addEventListeners();
    }

    _detectMobile() {
        return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
            || ('ontouchstart' in window && window.innerWidth < 900);
    }

    _detectPerformanceLevel() {
        let level = 3;

        if (this.isMobile) level--;

        const cores = navigator.hardwareConcurrency || 2;
        if (cores <= 4) level--;

        const memory = navigator.deviceMemory || 4;
        if (memory <= 2) level--;

        try {
            const canvas = document.createElement('canvas');
            const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
            if (!gl) level = Math.min(level, 1);
            const debugInfo = gl && gl.getExtension('WEBGL_debug_renderer_info');
            if (debugInfo) {
                const renderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL || 0x9246);
                if (/Adreno|Mali|PowerVR/i.test(renderer)) level = Math.min(level, 2);
            }
        } catch (e) {}

        return Math.max(1, level);
    }

    _getTargetPixelRatio() {
        if (this.performanceLevel <= 1) return Math.min(1, window.devicePixelRatio);
        if (this.performanceLevel === 2) return Math.min(1.5, window.devicePixelRatio);
        return window.devicePixelRatio;
    }

    init() {
        const width = this.container.clientWidth;
        const height = this.container.clientHeight;

        this.scene = new THREE.Scene();
        this.scene.background = new THREE.Color(0xf0f4f8);

        if (!this.useLowDetail) {
            this.scene.fog = new THREE.Fog(0xf0f4f8, 200, 500);
        }

        this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
        this.camera.position.set(0, 80, 200);
        this.camera.lookAt(0, 40, 0);

        this.renderer = new THREE.WebGLRenderer({
            antialias: this.performanceLevel >= 2,
            alpha: true,
            powerPreference: this.isMobile ? 'low-power' : 'high-performance'
        });
        this.renderer.setSize(width, height);
        this.renderer.setPixelRatio(this.targetPixelRatio);

        if (this.performanceLevel >= 2) {
            this.renderer.shadowMap.enabled = true;
            this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
        }
        this.container.appendChild(this.renderer.domElement);

        this._addLights();
        this._addGround();
        this._createBalanceLOD();
        this._setupControls();

        console.log(`[Balance3D] 设备: ${this.isMobile ? '移动端' : '桌面端'}, ` +
                    `性能等级: ${this.performanceLevel}, ` +
                    `DPR: ${this.targetPixelRatio.toFixed(2)}, ` +
                    `LOD: ${this.useLowDetail ? '低' : '高'}精度`);
    }

    _addLights() {
        const ambientIntensity = this.useLowDetail ? 0.8 : 0.6;
        const ambientLight = new THREE.AmbientLight(0xffffff, ambientIntensity);
        this.scene.add(ambientLight);

        const mainLight = new THREE.DirectionalLight(0xffffff, this.useLowDetail ? 0.7 : 0.8);
        mainLight.position.set(100, 150, 100);
        if (this.performanceLevel >= 2) {
            mainLight.castShadow = true;
            mainLight.shadow.mapSize.width = this.useLowDetail ? 512 : 2048;
            mainLight.shadow.mapSize.height = this.useLowDetail ? 512 : 2048;
            mainLight.shadow.camera.near = 0.5;
            mainLight.shadow.camera.far = 500;
            mainLight.shadow.camera.left = -150;
            mainLight.shadow.camera.right = 150;
            mainLight.shadow.camera.top = 150;
            mainLight.shadow.camera.bottom = -150;
        }
        this.scene.add(mainLight);

        if (this.performanceLevel >= 3) {
            const fillLight = new THREE.DirectionalLight(0x88aaff, 0.3);
            fillLight.position.set(-100, 80, -50);
            this.scene.add(fillLight);

            const bottomLight = new THREE.DirectionalLight(0xffddaa, 0.2);
            bottomLight.position.set(0, -50, 0);
            this.scene.add(bottomLight);
        }
    }

    _addGround() {
        if (this.useLowDetail) {
            const planeGeo = new THREE.PlaneGeometry(300, 300);
            const planeMat = new THREE.MeshBasicMaterial({ color: 0xe8e8e8 });
            const ground = new THREE.Mesh(planeGeo, planeMat);
            ground.rotation.x = -Math.PI / 2;
            this.scene.add(ground);
            return;
        }

        const groundGeometry = new THREE.CircleGeometry(180, this.performanceLevel >= 3 ? 64 : 32);
        const groundMaterial = new THREE.MeshStandardMaterial({
            color: 0xe8e8e8,
            roughness: 0.8,
            metalness: 0.1
        });
        const ground = new THREE.Mesh(groundGeometry, groundMaterial);
        ground.rotation.x = -Math.PI / 2;
        ground.position.y = 0;
        ground.receiveShadow = true;
        this.scene.add(ground);

        const gridHelper = new THREE.GridHelper(
            200,
            this.performanceLevel >= 3 ? 40 : 20,
            0xcccccc, 0xe0e0e0
        );
        gridHelper.position.y = 0.01;
        this.scene.add(gridHelper);
    }

    _createBalanceLOD() {
        this.balanceGroup = new THREE.Group();
        this.lod = new THREE.LOD();

        this.highDetailGroup = this._createBalanceDetail(true);
        this.lowDetailGroup = this._createBalanceDetail(false);

        this.lod.addLevel(this.highDetailGroup, 0);
        this.lod.addLevel(this.lowDetailGroup, AppConfig.balance3d.lodSwitchDistance);

        this.balanceGroup.add(this.lod);
        this.scene.add(this.balanceGroup);
    }

    _createBalanceDetail(highDetail) {
        const group = new THREE.Group();
        const segHigh = highDetail ? 32 : 8;
        const segMid = highDetail ? 16 : 6;
        const segLow = highDetail ? 8 : 4;

        const baseGeometry = new THREE.CylinderGeometry(25, 30, 8, segHigh);
        const baseMaterial = new THREE.MeshStandardMaterial({
            color: 0x5d4e37,
            roughness: 0.7,
            metalness: 0.3,
            flatShading: !highDetail
        });
        const base = new THREE.Mesh(baseGeometry, baseMaterial);
        base.position.y = 4;
        if (highDetail) { base.castShadow = true; base.receiveShadow = true; }
        group.add(base);

        const pillarGeometry = new THREE.CylinderGeometry(4, 5, 80, segMid);
        const pillarMaterial = new THREE.MeshStandardMaterial({
            color: 0x4a5568,
            roughness: 0.5,
            metalness: 0.6,
            flatShading: !highDetail
        });
        const pillar = new THREE.Mesh(pillarGeometry, pillarMaterial);
        pillar.position.y = 48;
        if (highDetail) { pillar.castShadow = true; pillar.receiveShadow = true; }
        group.add(pillar);

        const topBlockGeometry = new THREE.BoxGeometry(20, 10, 12);
        const topBlock = new THREE.Mesh(topBlockGeometry, pillarMaterial);
        topBlock.position.y = 93;
        if (highDetail) topBlock.castShadow = true;
        group.add(topBlock);

        const beamGeometry = new THREE.BoxGeometry(180, 4, 6);
        const beamMaterial = new THREE.MeshStandardMaterial({
            color: 0xffd700,
            roughness: 0.3,
            metalness: 0.8,
            emissive: 0xffb700,
            emissiveIntensity: highDetail ? 0.2 : 0.1,
            flatShading: !highDetail
        });
        this.beam = new THREE.Mesh(beamGeometry, beamMaterial);
        this.beam.position.y = 100;
        if (highDetail) this.beam.castShadow = true;
        this.beam.userData = { name: '横梁', type: 'beam' };
        this.clickableObjects.push(this.beam);
        group.add(this.beam);

        if (highDetail) {
            const beamGlowGeometry = new THREE.BoxGeometry(184, 8, 10);
            const beamGlowMaterial = new THREE.MeshBasicMaterial({
                color: 0xffd700,
                transparent: true,
                opacity: 0.15
            });
            const beamGlow = new THREE.Mesh(beamGlowGeometry, beamGlowMaterial);
            beamGlow.position.y = 100;
            this.beam.add(beamGlow);
        }

        const knifeMaterial = new THREE.MeshStandardMaterial({
            color: 0xff6b6b,
            roughness: 0.2,
            metalness: 0.9,
            emissive: 0xff3333,
            emissiveIntensity: highDetail ? 0.3 : 0.15,
            flatShading: !highDetail
        });

        this.knives = [];
        [[0, 98, '中央刀口'], [-85, 98, '左刀口'], [85, 98, '右刀口']].forEach(([x, y, name], idx) => {
            const knife = this._createKnifeEdge(knifeMaterial, highDetail, segMid, segLow);
            knife.position.set(x, y, 0);
            knife.userData = { name, type: 'knife' };
            this.clickableObjects.push(knife);
            this.knives.push(knife);
            group.add(knife);
        });

        this.pans = [];
        [-85, 85].forEach(x => {
            const pan = this._createPan(highDetail, segHigh, segMid);
            pan.position.set(x, 55, 0);
            this.pans.push(pan);
            group.add(pan);

            if (highDetail) {
                this._addSuspensionCords(x, 98, 55, group);
            }
        });

        if (highDetail) {
            this._addDecoration(group);
        }

        return group;
    }

    _createKnifeEdge(material, highDetail, segMid, segLow) {
        const group = new THREE.Group();

        const coneSeg = highDetail ? segMid : segLow;
        const coneGeometry = new THREE.ConeGeometry(3, 8, coneSeg);
        const cone = new THREE.Mesh(coneGeometry, material);
        cone.rotation.x = Math.PI;
        cone.position.y = -2;
        if (highDetail) cone.castShadow = true;
        group.add(cone);

        const capGeometry = new THREE.CylinderGeometry(4, 3, 4, coneSeg);
        const cap = new THREE.Mesh(capGeometry, material);
        cap.position.y = 2;
        if (highDetail) cap.castShadow = true;
        group.add(cap);

        if (highDetail) {
            const glowGeometry = new THREE.SphereGeometry(6, 16, 16);
            const glowMaterial = new THREE.MeshBasicMaterial({
                color: 0xff6b6b,
                transparent: true,
                opacity: 0.2
            });
            const glow = new THREE.Mesh(glowGeometry, glowMaterial);
            group.add(glow);
        }

        return group;
    }

    _createPan(highDetail, segHigh, segMid) {
        const group = new THREE.Group();

        const panSeg = highDetail ? segHigh : segMid;
        const panGeometry = new THREE.CylinderGeometry(25, 20, 3, panSeg);
        const panMaterial = new THREE.MeshStandardMaterial({
            color: 0x8b7355,
            roughness: 0.6,
            metalness: 0.2,
            flatShading: !highDetail
        });
        const pan = new THREE.Mesh(panGeometry, panMaterial);
        if (highDetail) { pan.castShadow = true; pan.receiveShadow = true; }
        group.add(pan);

        if (highDetail) {
            const rimGeometry = new THREE.TorusGeometry(25, 1.5, 4, panSeg);
            const rimMaterial = new THREE.MeshStandardMaterial({
                color: 0x6b5344,
                roughness: 0.5,
                metalness: 0.3
            });
            const rim = new THREE.Mesh(rimGeometry, rimMaterial);
            rim.rotation.x = Math.PI / 2;
            rim.position.y = 1.5;
            group.add(rim);
        }

        return group;
    }

    _addSuspensionCords(x, topY, bottomY, group) {
        const cordMaterial = new THREE.LineBasicMaterial({ color: 0x333333, linewidth: 1 });
        const offsets = [-15, 15];
        offsets.forEach(ox => {
            offsets.forEach(oz => {
                const points = [];
                points.push(new THREE.Vector3(x + ox, topY - 8, oz));
                points.push(new THREE.Vector3(x + ox * 1.5, bottomY + 3, oz * 1.5));
                const geometry = new THREE.BufferGeometry().setFromPoints(points);
                const line = new THREE.Line(geometry, cordMaterial);
                group.add(line);
            });
        });
    }

    _addDecoration(group) {
        const dragonGeometry = new THREE.TorusGeometry(8, 2, 4, 16);
        const dragonMaterial = new THREE.MeshStandardMaterial({
            color: 0x2c5530,
            roughness: 0.4,
            metalness: 0.5
        });

        [-70, 70].forEach(px => {
            const d = new THREE.Mesh(dragonGeometry, dragonMaterial);
            d.position.set(px, 103, 0);
            d.scale.set(0.8, 0.5, 0.5);
            this.beam.add(d);
        });

        const gemGeometry = new THREE.SphereGeometry(2, 8, 8);
        const gemMaterial = new THREE.MeshStandardMaterial({
            color: 0xff0000,
            roughness: 0.1,
            metalness: 0.9,
            emissive: 0xff0000,
            emissiveIntensity: 0.3
        });

        [-60, -30, 0, 30, 60].forEach(px => {
            const gem = new THREE.Mesh(gemGeometry, gemMaterial);
            gem.position.set(px, 102, 4);
            this.beam.add(gem);
        });
    }

    _setupControls() {
        let isDragging = false;
        let previousMousePosition = { x: 0, y: 0 };
        let spherical = { theta: 0, phi: Math.PI / 4, radius: 200 };
        let target = new THREE.Vector3(0, 50, 0);

        const updateCamera = () => {
            this.camera.position.x = target.x + spherical.radius * Math.sin(spherical.phi) * Math.sin(spherical.theta);
            this.camera.position.y = target.y + spherical.radius * Math.cos(spherical.phi);
            this.camera.position.z = target.z + spherical.radius * Math.sin(spherical.phi) * Math.cos(spherical.theta);
            this.camera.lookAt(target);
        };

        updateCamera();

        const canvas = this.renderer.domElement;
        canvas.style.touchAction = 'none';

        const handleMove = (dx, dy) => {
            if (this.autoRotate) return;
            const sensitivity = this.isMobile
                ? AppConfig.balance3d.mobile.rotateSensitivity
                : AppConfig.balance3d.mobile.desktopRotateSensitivity;
            spherical.theta -= dx * sensitivity;
            spherical.phi -= dy * sensitivity;
            spherical.phi = Math.max(0.1, Math.min(Math.PI - 0.1, spherical.phi));
            updateCamera();
        };

        const handleZoom = (delta) => {
            const zoomSensitivity = this.isMobile
                ? AppConfig.balance3d.mobile.zoomSensitivity
                : AppConfig.balance3d.mobile.desktopZoomSensitivity;
            spherical.radius += delta * zoomSensitivity;
            spherical.radius = Math.max(
                AppConfig.balance3d.minCameraDistance,
                Math.min(AppConfig.balance3d.maxCameraDistance, spherical.radius));
            updateCamera();
        };

        canvas.addEventListener('mousedown', (e) => {
            isDragging = true;
            previousMousePosition = { x: e.clientX, y: e.clientY };
        });

        canvas.addEventListener('mousemove', (e) => {
            if (isDragging) {
                const dx = e.clientX - previousMousePosition.x;
                const dy = e.clientY - previousMousePosition.y;
                handleMove(dx, dy);
                previousMousePosition = { x: e.clientX, y: e.clientY };
            }
            this._handlePointerMove(e.clientX, e.clientY);
        });

        canvas.addEventListener('mouseup', () => { isDragging = false; });
        canvas.addEventListener('mouseleave', () => { isDragging = false; });

        canvas.addEventListener('click', (e) => this._handleClick(e.clientX, e.clientY));

        canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            handleZoom(e.deltaY * 0.3);
        }, { passive: false });

        canvas.addEventListener('touchstart', (e) => {
            e.preventDefault();
            if (e.touches.length === 1) {
                isDragging = true;
                this._touchState.lastTouchX = e.touches[0].clientX;
                this._touchState.lastTouchY = e.touches[0].clientY;
            } else if (e.touches.length === 2) {
                this._touchState.isPinching = true;
                const dx = e.touches[0].clientX - e.touches[1].clientX;
                const dy = e.touches[0].clientY - e.touches[1].clientY;
                this._touchState.initialPinchDistance = Math.sqrt(dx * dx + dy * dy);
                this._touchState.initialSphericalRadius = spherical.radius;
            }
        }, { passive: false });

        canvas.addEventListener('touchmove', (e) => {
            e.preventDefault();
            if (e.touches.length === 1 && isDragging && !this._touchState.isPinching) {
                const dx = e.touches[0].clientX - this._touchState.lastTouchX;
                const dy = e.touches[0].clientY - this._touchState.lastTouchY;
                handleMove(dx, dy);
                this._touchState.lastTouchX = e.touches[0].clientX;
                this._touchState.lastTouchY = e.touches[0].clientY;
            } else if (e.touches.length === 2 && this._touchState.isPinching) {
                const dx = e.touches[0].clientX - e.touches[1].clientX;
                const dy = e.touches[0].clientY - e.touches[1].clientY;
                const distance = Math.sqrt(dx * dx + dy * dy);
                const scale = this._touchState.initialPinchDistance / distance;
                spherical.radius = Math.max(
                    AppConfig.balance3d.minCameraDistance,
                    Math.min(AppConfig.balance3d.maxCameraDistance,
                        this._touchState.initialSphericalRadius * scale));
                updateCamera();
            }
        }, { passive: false });

        canvas.addEventListener('touchend', (e) => {
            if (e.touches.length === 0) {
                isDragging = false;
                this._touchState.isPinching = false;
            }
        });

        this._spherical = spherical;
        this._target = target;
        this._updateCamera = updateCamera;
    }

    _handlePointerMove(clientX, clientY) {
        const rect = this.renderer.domElement.getBoundingClientRect();
        this.mouse.x = ((clientX - rect.left) / rect.width) * 2 - 1;
        this.mouse.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    }

    _handleClick(clientX, clientY) {
        if (this.useLowDetail) return;

        const rect = this.renderer.domElement.getBoundingClientRect();
        this.mouse.x = ((clientX - rect.left) / rect.width) * 2 - 1;
        this.mouse.y = -((clientY - rect.top) / rect.height) * 2 + 1;

        this.raycaster.setFromCamera(this.mouse, this.camera);

        const allMeshes = [];
        this.clickableObjects.forEach(obj => {
            if (obj.isMesh) allMeshes.push(obj);
            obj.traverse(child => { if (child.isMesh) allMeshes.push(child); });
        });

        const intersects = this.raycaster.intersectObjects(allMeshes, true);
        if (intersects.length > 0) {
            let clicked = intersects[0].object;
            while (clicked.parent && !clicked.userData.type) clicked = clicked.parent;
            if (clicked.userData.type && this.onBalanceClick) {
                this.onBalanceClick(clicked.userData);
            }
        }
    }

    updateBalanceData(data) {
        this.balanceData = data;

        if (data.leftArmLength && data.rightArmLength) {
            const leftRatio = data.leftArmLength / 150.0;
            const rightRatio = data.rightArmLength / 150.0;

            if (this.knives[1]) this.knives[1].position.x = -85 * leftRatio;
            if (this.knives[2]) this.knives[2].position.x = 85 * rightRatio;
            if (this.pans[0]) this.pans[0].position.x = -85 * leftRatio;
            if (this.pans[1]) this.pans[1].position.x = 85 * rightRatio;

            if (this.beam) {
                const totalLength = 85 * leftRatio + 85 * rightRatio;
                this.beam.scale.x = totalLength / 85;
            }
        }

        if (data.knifeEdgeWearDepth) {
            const wearScale = Math.min(1 + data.knifeEdgeWearDepth * 10, 3);
            this.knives.forEach(knife => { if (knife) knife.scale.set(wearScale, wearScale, wearScale); });
        }
    }

    setAlertState(isAlert, level) {
        if (!this.beam) return;
        if (isAlert) {
            const color = level === 'CRITICAL' ? 0xff0000 : 0xff8800;
            this.beam.material.emissive.setHex(color);
            this.beam.material.emissiveIntensity = this.useLowDetail ? 0.2 : 0.4;
        } else {
            this.beam.material.emissive.setHex(0xffb700);
            this.beam.material.emissiveIntensity = this.useLowDetail ? 0.1 : 0.2;
        }
    }

    toggleAutoRotate() {
        this.autoRotate = !this.autoRotate;
        return this.autoRotate;
    }

    resetView() {
        if (this._spherical) {
            this._spherical.theta = 0;
            this._spherical.phi = Math.PI / 4;
            this._spherical.radius = AppConfig.balance3d.maxCameraDistance / 2;
            this._target.set(0, 50, 0);
            this._updateCamera();
        }
    }

    _updatePhysics(dt) {
        if (!this.physicsEnabled) {
            this.swingAngle = Math.sin(this.time * AppConfig.balance3d.physics.lowDetailSineFreq)
                * AppConfig.balance3d.physics.lowDetailSineAmp;
            return;
        }

        this.physicsAccumulator += dt;
        while (this.physicsAccumulator >= this.physicsStep) {
            const restoring = -this.swingAngle * AppConfig.balance3d.physics.stiffness;
            const damping = -this.swingVelocity * AppConfig.balance3d.physics.damping;
            const accel = restoring + damping;
            this.swingVelocity += accel * this.physicsStep;
            this.swingAngle += this.swingVelocity * this.physicsStep;
            this.physicsAccumulator -= this.physicsStep;
        }
    }

    animate() {
        this.animationId = requestAnimationFrame(() => this.animate());

        const now = performance.now();
        const rawDt = (now - this.lastFrameTime) / 1000;
        this.lastFrameTime = now;

        if (rawDt > 0.25) return;

        this.frameDeltaSmoothing = this.frameDeltaSmoothing * 0.9 + rawDt * 0.1;
        const effectiveFps = 1 / Math.max(0.001, this.frameDeltaSmoothing);

        if (this.isMobile) {
            if (effectiveFps < AppConfig.balance3d.performance.fpsDropThrottle2
                    && this.performanceLevel > 1) {
                this.dynamicFpsThrottle = 2;
            } else if (effectiveFps < AppConfig.balance3d.performance.fpsDropThrottle3) {
                this.dynamicFpsThrottle = 3;
                if (!this.useLowDetail) {
                    this.useLowDetail = true;
                    this.physicsEnabled = false;
                    this.renderer.setPixelRatio(1);
                    console.log('[Balance3D] 性能不足，降级到低精度模式');
                }
            } else {
                this.dynamicFpsThrottle = 1;
            }
        }

        this.frameSkipCounter++;
        if (this.frameSkipCounter < this.dynamicFpsThrottle) return;
        this.frameSkipCounter = 0;

        const dt = rawDt * this.dynamicFpsThrottle;
        this.time += dt;

        if (this.autoRotate && this.balanceGroup) {
            this.balanceGroup.rotation.y +=
                AppConfig.balance3d.autoRotateSpeed * this.dynamicFpsThrottle;
        }

        this._updatePhysics(dt);

        if (this.beam) {
            this.beam.rotation.z = this.swingAngle;
            this.knives.forEach(k => { if (k) k.rotation.z = this.swingAngle; });

            const beamY = 100;
            const armLength = AppConfig.balance3d.physics.beamArmLength;
            if (this.pans[0]) {
                this.pans[0].position.x = -armLength * Math.cos(this.swingAngle);
                this.pans[0].position.y = beamY - 45 - armLength * Math.sin(this.swingAngle);
            }
            if (this.pans[1]) {
                this.pans[1].position.x = armLength * Math.cos(this.swingAngle);
                this.pans[1].position.y = beamY - 45 + armLength * Math.sin(this.swingAngle);
            }
        }

        if (this.knives[0] && !this.useLowDetail) {
            const pulse = 1 + Math.sin(this.time * 3) * 0.1;
            this.knives[0].scale.set(pulse, pulse, pulse);
        }

        if (this.lod) this.lod.update(this.camera);

        this.renderer.render(this.scene, this.camera);
    }

    resize() {
        const width = this.container.clientWidth;
        const height = this.container.clientHeight;
        this.camera.aspect = width / height;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(width, height);
    }

    _addEventListeners() {
        window.addEventListener('resize', () => this.resize());
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                this.lastFrameTime = 0;
            }
        });
    }

    dispose() {
        if (this.animationId) cancelAnimationFrame(this.animationId);
        if (this.renderer) {
            this.renderer.dispose();
            if (this.renderer.domElement.parentNode) {
                this.renderer.domElement.parentNode.removeChild(this.renderer.domElement);
            }
        }
    }
}
