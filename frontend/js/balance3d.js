class Balance3D {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        this.scene = null;
        this.camera = null;
        this.renderer = null;
        this.controls = null;
        this.balanceGroup = null;
        this.beam = null;
        this.leftKnife = null;
        this.rightKnife = null;
        this.centerKnife = null;
        this.leftPan = null;
        this.rightPan = null;
        this.autoRotate = false;
        this.raycaster = new THREE.Raycaster();
        this.mouse = new THREE.Vector2();
        this.clickableObjects = [];
        this.onBalanceClick = null;
        this.balanceData = null;
        this.animationId = null;
        this.time = 0;

        this.init();
        this.animate();
        this.addEventListeners();
    }

    init() {
        const width = this.container.clientWidth;
        const height = this.container.clientHeight;

        this.scene = new THREE.Scene();
        this.scene.background = new THREE.Color(0xf0f4f8);
        this.scene.fog = new THREE.Fog(0xf0f4f8, 200, 500);

        this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);
        this.camera.position.set(0, 80, 200);
        this.camera.lookAt(0, 40, 0);

        this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
        this.renderer.setSize(width, height);
        this.renderer.setPixelRatio(window.devicePixelRatio);
        this.renderer.shadowMap.enabled = true;
        this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
        this.container.appendChild(this.renderer.domElement);

        this.addLights();
        this.addGround();
        this.createBalance();
        this.setupOrbitControls();
    }

    addLights() {
        const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
        this.scene.add(ambientLight);

        const mainLight = new THREE.DirectionalLight(0xffffff, 0.8);
        mainLight.position.set(100, 150, 100);
        mainLight.castShadow = true;
        mainLight.shadow.mapSize.width = 2048;
        mainLight.shadow.mapSize.height = 2048;
        mainLight.shadow.camera.near = 0.5;
        mainLight.shadow.camera.far = 500;
        mainLight.shadow.camera.left = -150;
        mainLight.shadow.camera.right = 150;
        mainLight.shadow.camera.top = 150;
        mainLight.shadow.camera.bottom = -150;
        this.scene.add(mainLight);

        const fillLight = new THREE.DirectionalLight(0x88aaff, 0.3);
        fillLight.position.set(-100, 80, -50);
        this.scene.add(fillLight);

        const bottomLight = new THREE.DirectionalLight(0xffddaa, 0.2);
        bottomLight.position.set(0, -50, 0);
        this.scene.add(bottomLight);
    }

    addGround() {
        const groundGeometry = new THREE.CircleGeometry(180, 64);
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

        const gridHelper = new THREE.GridHelper(200, 40, 0xcccccc, 0xe0e0e0);
        gridHelper.position.y = 0.01;
        this.scene.add(gridHelper);
    }

    createBalance() {
        this.balanceGroup = new THREE.Group();

        const baseGeometry = new THREE.CylinderGeometry(25, 30, 8, 32);
        const baseMaterial = new THREE.MeshStandardMaterial({
            color: 0x5d4e37,
            roughness: 0.7,
            metalness: 0.3
        });
        const base = new THREE.Mesh(baseGeometry, baseMaterial);
        base.position.y = 4;
        base.castShadow = true;
        base.receiveShadow = true;
        this.balanceGroup.add(base);

        const pillarGeometry = new THREE.CylinderGeometry(4, 5, 80, 16);
        const pillarMaterial = new THREE.MeshStandardMaterial({
            color: 0x4a5568,
            roughness: 0.5,
            metalness: 0.6
        });
        const pillar = new THREE.Mesh(pillarGeometry, pillarMaterial);
        pillar.position.y = 48;
        pillar.castShadow = true;
        pillar.receiveShadow = true;
        this.balanceGroup.add(pillar);

        const topBlockGeometry = new THREE.BoxGeometry(20, 10, 12);
        const topBlock = new THREE.Mesh(topBlockGeometry, pillarMaterial);
        topBlock.position.y = 93;
        topBlock.castShadow = true;
        this.balanceGroup.add(topBlock);

        const beamGeometry = new THREE.BoxGeometry(180, 4, 6);
        const beamMaterial = new THREE.MeshStandardMaterial({
            color: 0xffd700,
            roughness: 0.3,
            metalness: 0.8,
            emissive: 0xffb700,
            emissiveIntensity: 0.2
        });
        this.beam = new THREE.Mesh(beamGeometry, beamMaterial);
        this.beam.position.y = 100;
        this.beam.castShadow = true;
        this.beam.userData = { name: '横梁', type: 'beam' };
        this.clickableObjects.push(this.beam);
        this.balanceGroup.add(this.beam);

        const beamGlowGeometry = new THREE.BoxGeometry(184, 8, 10);
        const beamGlowMaterial = new THREE.MeshBasicMaterial({
            color: 0xffd700,
            transparent: true,
            opacity: 0.15
        });
        const beamGlow = new THREE.Mesh(beamGlowGeometry, beamGlowMaterial);
        beamGlow.position.y = 100;
        this.beam.add(beamGlow);

        const knifeMaterial = new THREE.MeshStandardMaterial({
            color: 0xff6b6b,
            roughness: 0.2,
            metalness: 0.9,
            emissive: 0xff3333,
            emissiveIntensity: 0.3
        });

        this.centerKnife = this.createKnifeEdge(knifeMaterial);
        this.centerKnife.position.set(0, 98, 0);
        this.centerKnife.userData = { name: '中央刀口', type: 'knife' };
        this.clickableObjects.push(this.centerKnife);
        this.balanceGroup.add(this.centerKnife);

        this.leftKnife = this.createKnifeEdge(knifeMaterial);
        this.leftKnife.position.set(-85, 98, 0);
        this.leftKnife.userData = { name: '左刀口', type: 'knife' };
        this.clickableObjects.push(this.leftKnife);
        this.balanceGroup.add(this.leftKnife);

        this.rightKnife = this.createKnifeEdge(knifeMaterial);
        this.rightKnife.position.set(85, 98, 0);
        this.rightKnife.userData = { name: '右刀口', type: 'knife' };
        this.clickableObjects.push(this.rightKnife);
        this.balanceGroup.add(this.rightKnife);

        this.leftPan = this.createPan();
        this.leftPan.position.set(-85, 55, 0);
        this.balanceGroup.add(this.leftPan);

        this.rightPan = this.createPan();
        this.rightPan.position.set(85, 55, 0);
        this.balanceGroup.add(this.rightPan);

        this.addSuspensionCords(-85, 98, 55);
        this.addSuspensionCords(85, 98, 55);

        this.addDecoration();

        this.scene.add(this.balanceGroup);
    }

    createKnifeEdge(material) {
        const group = new THREE.Group();

        const coneGeometry = new THREE.ConeGeometry(3, 8, 16);
        const cone = new THREE.Mesh(coneGeometry, material);
        cone.rotation.x = Math.PI;
        cone.position.y = -2;
        cone.castShadow = true;
        group.add(cone);

        const capGeometry = new THREE.CylinderGeometry(4, 3, 4, 16);
        const cap = new THREE.Mesh(capGeometry, material);
        cap.position.y = 2;
        cap.castShadow = true;
        group.add(cap);

        const glowGeometry = new THREE.SphereGeometry(6, 16, 16);
        const glowMaterial = new THREE.MeshBasicMaterial({
            color: 0xff6b6b,
            transparent: true,
            opacity: 0.2
        });
        const glow = new THREE.Mesh(glowGeometry, glowMaterial);
        group.add(glow);

        return group;
    }

    createPan() {
        const group = new THREE.Group();

        const panGeometry = new THREE.CylinderGeometry(25, 20, 3, 32);
        const panMaterial = new THREE.MeshStandardMaterial({
            color: 0x8b7355,
            roughness: 0.6,
            metalness: 0.2
        });
        const pan = new THREE.Mesh(panGeometry, panMaterial);
        pan.castShadow = true;
        pan.receiveShadow = true;
        group.add(pan);

        const rimGeometry = new THREE.TorusGeometry(25, 1.5, 8, 32);
        const rimMaterial = new THREE.MeshStandardMaterial({
            color: 0x6b5344,
            roughness: 0.5,
            metalness: 0.3
        });
        const rim = new THREE.Mesh(rimGeometry, rimMaterial);
        rim.rotation.x = Math.PI / 2;
        rim.position.y = 1.5;
        group.add(rim);

        return group;
    }

    addSuspensionCords(x, topY, bottomY) {
        const cordMaterial = new THREE.LineBasicMaterial({
            color: 0x333333,
            linewidth: 1
        });

        const offsets = [-15, 15];
        offsets.forEach(ox => {
            offsets.forEach(oz => {
                const points = [];
                points.push(new THREE.Vector3(x + ox, topY - 8, oz));
                points.push(new THREE.Vector3(x + ox * 1.5, bottomY + 3, oz * 1.5));

                const geometry = new THREE.BufferGeometry().setFromPoints(points);
                const line = new THREE.Line(geometry, cordMaterial);
                this.balanceGroup.add(line);
            });
        });
    }

    addDecoration() {
        const dragonGeometry = new THREE.TorusGeometry(8, 2, 8, 16);
        const dragonMaterial = new THREE.MeshStandardMaterial({
            color: 0x2c5530,
            roughness: 0.4,
            metalness: 0.5
        });

        const leftDragon = new THREE.Mesh(dragonGeometry, dragonMaterial);
        leftDragon.position.set(-70, 103, 0);
        leftDragon.scale.set(0.8, 0.5, 0.5);
        this.beam.add(leftDragon);

        const rightDragon = new THREE.Mesh(dragonGeometry, dragonMaterial);
        rightDragon.position.set(70, 103, 0);
        rightDragon.scale.set(0.8, 0.5, 0.5);
        this.beam.add(rightDragon);

        const gemGeometry = new THREE.SphereGeometry(2, 16, 16);
        const gemMaterial = new THREE.MeshStandardMaterial({
            color: 0xff0000,
            roughness: 0.1,
            metalness: 0.9,
            emissive: 0xff0000,
            emissiveIntensity: 0.3
        });

        const gemPositions = [-60, -30, 0, 30, 60];
        gemPositions.forEach(px => {
            const gem = new THREE.Mesh(gemGeometry, gemMaterial);
            gem.position.set(px, 102, 4);
            this.beam.add(gem);
        });
    }

    setupOrbitControls() {
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

        canvas.addEventListener('mousedown', (e) => {
            isDragging = true;
            previousMousePosition = { x: e.clientX, y: e.clientY };
        });

        canvas.addEventListener('mousemove', (e) => {
            if (isDragging && !this.autoRotate) {
                const deltaX = e.clientX - previousMousePosition.x;
                const deltaY = e.clientY - previousMousePosition.y;

                spherical.theta -= deltaX * 0.01;
                spherical.phi -= deltaY * 0.01;
                spherical.phi = Math.max(0.1, Math.min(Math.PI - 0.1, spherical.phi));

                updateCamera();
                previousMousePosition = { x: e.clientX, y: e.clientY };
            }

            this.handleMouseMove(e);
        });

        canvas.addEventListener('mouseup', () => {
            isDragging = false;
        });

        canvas.addEventListener('mouseleave', () => {
            isDragging = false;
        });

        canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            spherical.radius += e.deltaY * 0.3;
            spherical.radius = Math.max(80, Math.min(400, spherical.radius));
            updateCamera();
        });

        canvas.addEventListener('click', (e) => this.handleClick(e));

        this._spherical = spherical;
        this._target = target;
        this._updateCamera = updateCamera;
    }

    handleMouseMove(event) {
        const rect = this.renderer.domElement.getBoundingClientRect();
        this.mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        this.mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    }

    handleClick(event) {
        this.raycaster.setFromCamera(this.mouse, this.camera);

        const allMeshes = [];
        this.clickableObjects.forEach(obj => {
            if (obj.isMesh) {
                allMeshes.push(obj);
            }
            obj.traverse(child => {
                if (child.isMesh) {
                    allMeshes.push(child);
                }
            });
        });

        const intersects = this.raycaster.intersectObjects(allMeshes, true);

        if (intersects.length > 0) {
            let clickedObject = intersects[0].object;
            while (clickedObject.parent && !clickedObject.userData.type) {
                clickedObject = clickedObject.parent;
            }

            if (clickedObject.userData.type && this.onBalanceClick) {
                this.onBalanceClick(clickedObject.userData);
            }
        }
    }

    updateBalanceData(data) {
        this.balanceData = data;

        if (data.leftArmLength && data.rightArmLength) {
            const leftRatio = data.leftArmLength / 150.0;
            const rightRatio = data.rightArmLength / 150.0;

            if (this.leftKnife) {
                this.leftKnife.position.x = -85 * leftRatio;
            }
            if (this.rightKnife) {
                this.rightKnife.position.x = 85 * rightRatio;
            }
            if (this.leftPan) {
                this.leftPan.position.x = -85 * leftRatio;
            }
            if (this.rightPan) {
                this.rightPan.position.x = 85 * rightRatio;
            }

            if (this.beam) {
                const totalLength = 85 * leftRatio + 85 * rightRatio;
                this.beam.scale.x = (totalLength) / 85;
            }
        }

        if (data.knifeEdgeWearDepth) {
            const wearScale = 1 + data.knifeEdgeWearDepth * 10;
            [this.centerKnife, this.leftKnife, this.rightKnife].forEach(knife => {
                if (knife) {
                    knife.scale.set(wearScale, wearScale, wearScale);
                }
            });
        }
    }

    setAlertState(isAlert, level) {
        if (isAlert) {
            const alertColor = level === 'CRITICAL' ? 0xff0000 : 0xff8800;
            this.beam.material.emissive.setHex(alertColor);
            this.beam.material.emissiveIntensity = 0.4;
        } else {
            this.beam.material.emissive.setHex(0xffb700);
            this.beam.material.emissiveIntensity = 0.2;
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
            this._spherical.radius = 200;
            this._target.set(0, 50, 0);
            this._updateCamera();
        }
    }

    animate() {
        this.animationId = requestAnimationFrame(() => this.animate());

        this.time += 0.016;

        if (this.autoRotate && this.balanceGroup) {
            this.balanceGroup.rotation.y += 0.005;
        }

        if (this.beam) {
            const swingAngle = Math.sin(this.time * 1.5) * 0.02;
            this.beam.rotation.z = swingAngle;

            if (this.leftKnife) this.leftKnife.rotation.z = swingAngle;
            if (this.rightKnife) this.rightKnife.rotation.z = swingAngle;

            const beamY = 100;
            const armLength = 85;
            if (this.leftPan) {
                const x = -armLength * Math.cos(swingAngle);
                const y = beamY - 45 - armLength * Math.sin(swingAngle);
                this.leftPan.position.x = x;
                this.leftPan.position.y = y;
            }
            if (this.rightPan) {
                const x = armLength * Math.cos(swingAngle);
                const y = beamY - 45 + armLength * Math.sin(swingAngle);
                this.rightPan.position.x = x;
                this.rightPan.position.y = y;
            }
        }

        if (this.centerKnife) {
            const pulse = 1 + Math.sin(this.time * 3) * 0.1;
            this.centerKnife.scale.set(pulse, pulse, pulse);
        }

        this.renderer.render(this.scene, this.camera);
    }

    resize() {
        const width = this.container.clientWidth;
        const height = this.container.clientHeight;

        this.camera.aspect = width / height;
        this.camera.updateProjectionMatrix();

        this.renderer.setSize(width, height);
    }

    addEventListeners() {
        window.addEventListener('resize', () => this.resize());
    }

    dispose() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
        }
        if (this.renderer) {
            this.renderer.dispose();
            if (this.renderer.domElement.parentNode) {
                this.renderer.domElement.parentNode.removeChild(this.renderer.domElement);
            }
        }
    }
}
