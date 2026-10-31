// State
let intervals = [
    { id: 1, name: 'Task A', start: 10, end: 40, color: 'hsl(200, 80%, 60%)', effects: [{id: 1, type: 'pulse', height: 4}] },
    { id: 2, name: 'Task B', start: 30, end: 70, color: 'hsl(280, 80%, 60%)', effects: [{id: 2, type: 'pulse', height: 5}] },
    { id: 3, name: 'Prod C', start: 60, end: 80, color: 'hsl(140, 80%, 60%)', effects: [{id: 3, type: 'stepAtEnd', height: -2}] }
];
let nextIntervalId = 4;
let nextEffectId = 4;

let timeMax = 100;

const state = {
    constraintMin: 0,
    constraintMax: 8,
    dragging: null,
    currentTab: 'charts'
};

// Nice vibrant colors
const colors = ['hsl(200, 85%, 60%)', 'hsl(280, 80%, 65%)', 'hsl(140, 75%, 60%)', 'hsl(35, 90%, 60%)', 'hsl(320, 80%, 65%)', 'hsl(0, 80%, 65%)'];

// DOM Elements
const intervalsListEl = document.getElementById('intervals-list');
const ganttAreaEl = document.getElementById('gantt-area');
const ganttAxisEl = document.getElementById('gantt-axis');
const cumulSvgEl = document.getElementById('cumul-svg');
const yAxisEl = document.getElementById('cumul-y-axis');
const referencesBtn = document.getElementById('references-btn');
const mobileReferencesBtn = document.getElementById('mobile-references-btn');
const referencesModal = document.getElementById('references-modal');
const closeModalBtn = document.querySelector('.close-modal');
const dragIndicatorEl = document.getElementById('drag-indicator');
const appContainer = document.querySelector('.app-container');

function init() {
    const minInput = document.getElementById('global-min');
    const maxInput = document.getElementById('global-max');
    
    minInput.value = state.constraintMin;
    maxInput.value = state.constraintMax;
    
    minInput.addEventListener('input', (e) => {
        state.constraintMin = parseFloat(e.target.value) || 0;
        renderAll();
    });
    maxInput.addEventListener('input', (e) => {
        state.constraintMax = parseFloat(e.target.value) || 0;
        renderAll();
    });
    
    const addIntervalBtn = document.getElementById('add-interval-btn');
    if (addIntervalBtn) {
        addIntervalBtn.addEventListener('click', addNewInterval);
    }
    
    const quickAddBtn = document.getElementById('quick-add-task-btn');
    if (quickAddBtn) {
        quickAddBtn.addEventListener('click', addNewInterval);
    }

    // Pointer and Touch drag listeners
    document.addEventListener('pointermove', handlePointerMove, { passive: false });
    document.addEventListener('pointerup', handlePointerUp);
    document.addEventListener('pointercancel', handlePointerUp);

    // Fallback for touchmove scrolling prevention while dragging
    document.addEventListener('touchmove', (e) => {
        if (state.dragging) {
            e.preventDefault();
        }
    }, { passive: false });

    // Resize handling & observer
    window.addEventListener('resize', handleResize);
    if (window.ResizeObserver) {
        const ro = new ResizeObserver(() => {
            renderGantt();
            renderCumul();
        });
        if (ganttAreaEl) ro.observe(ganttAreaEl);
        if (cumulSvgEl && cumulSvgEl.parentElement) ro.observe(cumulSvgEl.parentElement);
    }
    
    // References Modal
    const openModal = () => { referencesModal.style.display = 'flex'; };
    const closeModal = () => { referencesModal.style.display = 'none'; };
    
    if (referencesBtn) referencesBtn.addEventListener('click', openModal);
    if (mobileReferencesBtn) mobileReferencesBtn.addEventListener('click', openModal);
    if (closeModalBtn) closeModalBtn.addEventListener('click', closeModal);
    
    window.addEventListener('click', (e) => {
        if (e.target === referencesModal) closeModal();
    });
    window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && referencesModal.style.display === 'flex') closeModal();
    });
    
    // Mobile Tabs & Navigation
    initMobileNav();
    
    // Initial Render
    renderAll();
}

function initMobileNav() {
    const mobileTabs = document.querySelectorAll('.mobile-tab');
    mobileTabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const targetView = tab.getAttribute('data-tab');
            switchMobileView(targetView);
        });
    });

    const toConfigBtn = document.getElementById('quick-edit-config-btn');
    if (toConfigBtn) {
        toConfigBtn.addEventListener('click', () => switchMobileView('config'));
    }

    const toChartsBtn = document.getElementById('quick-view-charts-btn');
    if (toChartsBtn) {
        toChartsBtn.addEventListener('click', () => switchMobileView('charts'));
    }
}

function switchMobileView(viewName) {
    state.currentTab = viewName;
    if (appContainer) {
        appContainer.setAttribute('data-view', viewName);
    }
    
    document.querySelectorAll('.mobile-tab').forEach(tab => {
        const isActive = tab.getAttribute('data-tab') === viewName;
        tab.classList.toggle('active', isActive);
        tab.setAttribute('aria-selected', isActive ? 'true' : 'false');
    });

    // When switching view to charts or split, re-render so dimensions are measured accurately
    requestAnimationFrame(() => {
        renderGantt();
        renderCumul();
    });
}

function addNewInterval() {
    intervals.push({
        id: nextIntervalId++,
        name: `Task ${String.fromCharCode(64 + ((nextIntervalId - 2) % 26) + 1)}`,
        start: 10,
        end: 35,
        color: colors[intervals.length % colors.length],
        effects: [{id: nextEffectId++, type: 'pulse', height: 2}]
    });
    renderAll();
}

function handleResize() {
    renderGantt();
    renderCumul();
}

function renderAll() {
    updateBadges();
    renderSidebar();
    renderGantt();
    renderCumul();
}

function updateBadges() {
    const badgeCount = document.getElementById('intervals-count-badge');
    if (badgeCount) {
        badgeCount.innerText = intervals.length;
    }
}

function updateFeasibilityBadge(isViolated) {
    const desktopBadge = document.getElementById('desktop-status-badge');
    const mobileBadge = document.getElementById('mobile-status-badge');
    
    const className = isViolated ? 'status-badge violated' : 'status-badge feasible';
    
    if (desktopBadge) {
        desktopBadge.className = className;
        desktopBadge.innerHTML = `<span class="badge-dot"></span><span class="badge-text">${isViolated ? 'Capacity Exceeded' : 'Feasible'}</span>`;
    }
    if (mobileBadge) {
        mobileBadge.className = className;
        mobileBadge.innerHTML = `<span class="badge-dot"></span><span class="badge-text">${isViolated ? 'Violated' : 'Feasible'}</span>`;
    }
}

function renderSidebar() {
    intervalsListEl.innerHTML = '';
    intervals.forEach((inv) => {
        const card = document.createElement('div');
        card.className = 'interval-card';
        card.style.borderLeftColor = inv.color;
        
        // Header
        const header = document.createElement('div');
        header.className = 'interval-card-header';
        header.innerHTML = `
            <div class="interval-name" style="color: ${inv.color}">${inv.name}</div>
            <button class="btn danger" onclick="removeInterval(${inv.id})">Del</button>
        `;
        card.appendChild(header);
        
        // Times
        const times = document.createElement('div');
        times.className = 'interval-times';
        times.innerHTML = `
            <div class="input-group">
                <label>Start</label>
                <input type="number" inputmode="numeric" value="${Math.round(inv.start)}" onchange="updateInterval(${inv.id}, 'start', this.value)">
            </div>
            <div class="input-group">
                <label>End</label>
                <input type="number" inputmode="numeric" value="${Math.round(inv.end)}" onchange="updateInterval(${inv.id}, 'end', this.value)">
            </div>
        `;
        card.appendChild(times);
        
        // Effects
        const effectsList = document.createElement('div');
        effectsList.className = 'effects-list';
        inv.effects.forEach(eff => {
            const effItem = document.createElement('div');
            effItem.className = 'effect-item';
            effItem.innerHTML = `
                <select onchange="updateEffect(${inv.id}, ${eff.id}, 'type', this.value)">
                    <option value="pulse" ${eff.type === 'pulse' ? 'selected' : ''}>pulse</option>
                    <option value="stepAtStart" ${eff.type === 'stepAtStart' ? 'selected' : ''}>stepAtStart</option>
                    <option value="stepAtEnd" ${eff.type === 'stepAtEnd' ? 'selected' : ''}>stepAtEnd</option>
                </select>
                <input type="number" inputmode="numeric" value="${eff.height}" step="1" onchange="updateEffect(${inv.id}, ${eff.id}, 'height', this.value)">
                <button class="btn small danger" onclick="removeEffect(${inv.id}, ${eff.id})">X</button>
            `;
            effectsList.appendChild(effItem);
        });
        card.appendChild(effectsList);
        
        const addEffBtn = document.createElement('button');
        addEffBtn.className = 'btn small glass-btn';
        addEffBtn.innerText = '+ Add Effect';
        addEffBtn.onclick = () => addEffect(inv.id);
        card.appendChild(addEffBtn);
        
        intervalsListEl.appendChild(card);
    });
}

// Window functions to be accessible from inline HTML
window.updateInterval = function(id, field, value) {
    const inv = intervals.find(i => i.id === id);
    if (inv) {
        inv[field] = Math.max(0, Math.min(timeMax, parseFloat(value) || 0));
        if (inv.start > inv.end) {
            if (field === 'start') inv.end = inv.start;
            else inv.start = inv.end;
        }
        renderAll();
    }
}

window.removeInterval = function(id) {
    intervals = intervals.filter(i => i.id !== id);
    renderAll();
}

window.addEffect = function(intervalId) {
    const inv = intervals.find(i => i.id === intervalId);
    if (inv) {
        inv.effects.push({ id: nextEffectId++, type: 'pulse', height: 1 });
        renderAll();
    }
}

window.updateEffect = function(invId, effId, field, value) {
    const inv = intervals.find(i => i.id === invId);
    if (inv) {
        const eff = inv.effects.find(e => e.id === effId);
        if (eff) {
            eff[field] = field === 'height' ? parseFloat(value) || 0 : value;
            renderAll();
        }
    }
}

window.removeEffect = function(invId, effId) {
    const inv = intervals.find(i => i.id === invId);
    if (inv) {
        inv.effects = inv.effects.filter(e => e.id !== effId);
        renderAll();
    }
}

function renderGantt() {
    ganttAreaEl.innerHTML = '';
    ganttAxisEl.innerHTML = '';
    
    const ganttWidth = ganttAreaEl.clientWidth || 300;
    // Responsive tick step: on narrow viewports use step 20, else step 10
    const tickStep = ganttWidth < 450 ? 20 : 10;
    
    // Draw Axis
    for (let t = 0; t <= timeMax; t += tickStep) {
        const tick = document.createElement('div');
        tick.className = 'tick';
        tick.style.left = `${(t / timeMax) * 100}%`;
        tick.innerText = t;
        ganttAxisEl.appendChild(tick);
    }
    
    // Draw Bars
    intervals.forEach((inv, index) => {
        const bar = document.createElement('div');
        bar.className = 'gantt-bar';
        bar.setAttribute('data-interval-id', inv.id);
        bar.style.top = `${index * 46 + 10}px`;
        bar.style.left = `${(inv.start / timeMax) * 100}%`;
        bar.style.width = `${((inv.end - inv.start) / timeMax) * 100}%`;
        bar.style.backgroundColor = inv.color;
        bar.innerText = inv.name;
        
        // Resize Handles
        const leftH = document.createElement('div');
        leftH.className = 'resize-handle left';
        leftH.setAttribute('title', 'Drag to resize start');
        
        const rightH = document.createElement('div');
        rightH.className = 'resize-handle right';
        rightH.setAttribute('title', 'Drag to resize end');
        
        leftH.onpointerdown = (e) => startDrag(e, 'resize-l', inv.id);
        rightH.onpointerdown = (e) => startDrag(e, 'resize-r', inv.id);
        
        // Drag Body
        bar.onpointerdown = (e) => {
            if (e.target !== leftH && e.target !== rightH) {
                startDrag(e, 'move', inv.id);
            }
        };
        
        bar.appendChild(leftH);
        bar.appendChild(rightH);
        ganttAreaEl.appendChild(bar);
    });
}

function startDrag(e, type, id) {
    if (e.button !== undefined && e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    
    const inv = intervals.find(i => i.id === id);
    if (!inv) return;
    
    const clientX = e.clientX !== undefined ? e.clientX : (e.touches && e.touches[0] ? e.touches[0].clientX : 0);
    
    state.dragging = {
        type,
        id,
        startX: clientX,
        initialStart: inv.start,
        initialEnd: inv.end,
        pointerId: e.pointerId
    };
    
    if (e.target && e.target.setPointerCapture && e.pointerId !== undefined) {
        try {
            e.target.setPointerCapture(e.pointerId);
            state.capturedElement = e.target;
        } catch (_) {}
    }
    
    document.body.style.cursor = type === 'move' ? 'grabbing' : 'ew-resize';
    const barEl = document.querySelector(`[data-interval-id="${id}"]`);
    if (barEl) barEl.classList.add('is-dragging');
    
    showDragIndicator(inv);
}

function showDragIndicator(inv) {
    if (!dragIndicatorEl) return;
    dragIndicatorEl.style.display = 'inline-block';
    updateDragIndicator(inv);
}

function updateDragIndicator(inv) {
    if (!dragIndicatorEl) return;
    const s = Math.round(inv.start);
    const e = Math.round(inv.end);
    const dur = e - s;
    dragIndicatorEl.innerText = `${inv.name}: [${s} → ${e}] (${dur}h)`;
}

function hideDragIndicator() {
    if (!dragIndicatorEl) return;
    dragIndicatorEl.style.display = 'none';
}

function handlePointerMove(e) {
    if (!state.dragging) return;
    e.preventDefault();
    
    const clientX = e.clientX !== undefined ? e.clientX : (e.touches && e.touches[0] ? e.touches[0].clientX : 0);
    
    const ganttRect = ganttAreaEl.getBoundingClientRect();
    const pixelsPerTime = ganttRect.width / timeMax;
    if (pixelsPerTime <= 0) return;
    
    const deltaX = clientX - state.dragging.startX;
    const deltaTime = deltaX / pixelsPerTime;
    
    const inv = intervals.find(i => i.id === state.dragging.id);
    if (!inv) return;
    
    if (state.dragging.type === 'move') {
        const duration = state.dragging.initialEnd - state.dragging.initialStart;
        let newStart = state.dragging.initialStart + deltaTime;
        if (newStart < 0) newStart = 0;
        if (newStart + duration > timeMax) newStart = timeMax - duration;
        
        inv.start = newStart;
        inv.end = newStart + duration;
    } else if (state.dragging.type === 'resize-l') {
        let newStart = state.dragging.initialStart + deltaTime;
        if (newStart < 0) newStart = 0;
        if (newStart >= inv.end - 1) newStart = inv.end - 1; // Min 1 time unit
        inv.start = newStart;
    } else if (state.dragging.type === 'resize-r') {
        let newEnd = state.dragging.initialEnd + deltaTime;
        if (newEnd > timeMax) newEnd = timeMax;
        if (newEnd <= inv.start + 1) newEnd = inv.start + 1;
        inv.end = newEnd;
    }
    
    // Fast update during drag without full sidebar recreation
    const barEl = document.querySelector(`[data-interval-id="${inv.id}"]`);
    if (barEl) {
        barEl.style.left = `${(inv.start / timeMax) * 100}%`;
        barEl.style.width = `${((inv.end - inv.start) / timeMax) * 100}%`;
    }
    
    updateDragIndicator(inv);
    renderCumul();
}

function handlePointerUp() {
    if (state.dragging) {
        if (state.capturedElement && state.capturedElement.releasePointerCapture && state.dragging.pointerId !== undefined) {
            try {
                state.capturedElement.releasePointerCapture(state.dragging.pointerId);
            } catch (_) {}
        }
        state.capturedElement = null;
        
        const id = state.dragging.id;
        state.dragging = null;
        document.body.style.cursor = 'default';
        
        const barEl = document.querySelector(`[data-interval-id="${id}"]`);
        if (barEl) barEl.classList.remove('is-dragging');
        
        hideDragIndicator();
        
        // Snap intervals to closest integer for cleaner look when dropping
        intervals.forEach(inv => {
            inv.start = Math.round(inv.start);
            inv.end = Math.round(inv.end);
        });
        
        renderAll();
    }
}

// Compute the union of intervals where the function is defined
function computeDefinedSegments() {
    let definedIntervals = [];
    intervals.forEach(inv => {
        definedIntervals.push({ start: inv.start, end: inv.end });
        
        let hasStepAtStart = inv.effects.some(e => e.type === 'stepAtStart');
        let hasStepAtEnd = inv.effects.some(e => e.type === 'stepAtEnd');
        if (hasStepAtStart) definedIntervals.push({ start: inv.start, end: timeMax });
        if (hasStepAtEnd) definedIntervals.push({ start: inv.end, end: timeMax });
    });
    
    if (definedIntervals.length === 0) return [];
    
    definedIntervals.sort((a, b) => a.start - b.start);
    let merged = [{ start: definedIntervals[0].start, end: definedIntervals[0].end }];
    
    for (let i = 1; i < definedIntervals.length; i++) {
        let last = merged[merged.length - 1];
        let curr = definedIntervals[i];
        if (curr.start <= last.end) {
            last.end = Math.max(last.end, curr.end);
        } else {
            merged.push({ start: curr.start, end: curr.end });
        }
    }
    return merged;
}

// Generates the array of constant segments {start, end, level, isDefined}
function computeProfile() {
    let events = [];
    
    intervals.forEach(inv => {
        events.push({ time: inv.start, delta: 0, intervalId: inv.id });
        events.push({ time: inv.end, delta: 0, intervalId: inv.id });

        inv.effects.forEach(eff => {
            if (eff.type === 'pulse') {
                events.push({ time: inv.start, delta: eff.height, intervalId: inv.id });
                events.push({ time: inv.end, delta: -eff.height, intervalId: inv.id });
            } else if (eff.type === 'stepAtStart') {
                events.push({ time: inv.start, delta: eff.height, intervalId: inv.id });
            } else if (eff.type === 'stepAtEnd') {
                events.push({ time: inv.end, delta: eff.height, intervalId: inv.id });
            }
        });
    });

    events.sort((a, b) => a.time - b.time);

    let level = 0;
    let profile = [];
    
    if (events.length === 0) {
        return [{start: 0, end: timeMax, level: 0}];
    }

    let currentTime = 0;
    
    if (events[0].time > 0) {
        profile.push({ start: 0, end: events[0].time, level: level });
        currentTime = events[0].time;
    }

    for (let i = 0; i < events.length; i++) {
        let e = events[i];
        
        if (e.time > currentTime) {
            profile.push({ start: currentTime, end: e.time, level: level });
            currentTime = e.time;
        }
        
        level += e.delta;
        
        while (i + 1 < events.length && events[i + 1].time === e.time) {
            i++;
            level += events[i].delta;
        }
    }

    if (currentTime < timeMax) {
        profile.push({ start: currentTime, end: timeMax, level: level });
    }

    // Determine which profile segments are "defined"
    let definedSegments = computeDefinedSegments();
    profile.forEach(p => {
        let mid = (p.start + p.end) / 2;
        p.isDefined = definedSegments.some(d => mid >= d.start && mid <= d.end);
    });

    return profile;
}

function renderCumul() {
    cumulSvgEl.innerHTML = '';
    yAxisEl.innerHTML = '';
    
    const parentArea = cumulSvgEl.parentElement;
    const width = (parentArea && parentArea.clientWidth) ? parentArea.clientWidth : (cumulSvgEl.clientWidth || 300);
    const height = (parentArea && parentArea.clientHeight) ? parentArea.clientHeight : (cumulSvgEl.clientHeight || 200);
    
    cumulSvgEl.setAttribute('viewBox', `0 0 ${width} ${height}`);
    
    const profile = computeProfile();
    
    // Check violation
    let isViolated = false;
    profile.forEach(p => {
        if (p.isDefined && (p.level < state.constraintMin || p.level > state.constraintMax)) {
            isViolated = true;
        }
    });
    updateFeasibilityBadge(isViolated);
    
    // Auto-scale Y Axis based on constraints and actual defined data
    let minLvl = Math.min(state.constraintMin, 0);
    let maxLvl = Math.max(state.constraintMax, 1);
    profile.forEach(p => {
        if (p.isDefined) {
            if (p.level < minLvl) minLvl = p.level;
            if (p.level > maxLvl) maxLvl = p.level;
        }
    });
    
    minLvl -= 2; 
    maxLvl += 2;
    
    const timeToX = (t) => (t / timeMax) * width;
    const capToY = (c) => height - ((c - minLvl) / (maxLvl - minLvl)) * height;
    
    // Draw Y Ticks & Grid (Integers only)
    let yRange = maxLvl - minLvl;
    const isSmall = height < 200 || width < 450;
    let step = Math.max(1, Math.ceil(yRange / (isSmall ? 4 : 6)));
    let startTick = Math.ceil(minLvl / step) * step;

    for (let val = startTick; val <= maxLvl; val += step) {
        const y = capToY(val);
        const tick = document.createElement('div');
        tick.className = 'y-tick';
        tick.style.top = `${y}px`;
        tick.innerText = val;
        yAxisEl.appendChild(tick);
        
        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', 0);
        line.setAttribute('x2', width);
        line.setAttribute('y1', y);
        line.setAttribute('y2', y);
        line.setAttribute('class', 'grid-line');
        cumulSvgEl.appendChild(line);
    }
    
    // Draw Constraint Lines (Min/Max limits)
    [state.constraintMin, state.constraintMax].forEach(val => {
        const y = capToY(val);
        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', 0);
        line.setAttribute('x2', width);
        line.setAttribute('y1', y);
        line.setAttribute('y2', y);
        line.setAttribute('class', 'constraint-line');
        cumulSvgEl.appendChild(line);
    });

    // 1. Draw Violation Backgrounds (red zones), only where defined
    profile.forEach(p => {
        if (p.isDefined && (p.level < state.constraintMin || p.level > state.constraintMax)) {
            const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
            rect.setAttribute('x', timeToX(p.start));
            rect.setAttribute('y', 0);
            rect.setAttribute('width', Math.max(1, timeToX(p.end) - timeToX(p.start)));
            rect.setAttribute('height', height);
            rect.setAttribute('class', 'violation-fill');
            cumulSvgEl.appendChild(rect);
        }
    });
    
    if (profile.length === 0) return;
    
    // 2. Compute Paths for the Cumulative Curve
    let pathD = '';
    let fillD = '';
    
    let isPenDown = false;
    
    for (let i = 0; i < profile.length; i++) {
        const p = profile[i];
        
        if (!p.isDefined) {
            if (isPenDown) {
                fillD += ` L ${timeToX(profile[i-1].end)} ${capToY(0)} Z `;
                isPenDown = false;
            }
            continue;
        }

        const x1 = timeToX(p.start);
        const x2 = timeToX(p.end);
        const y = capToY(p.level);
        
        if (!isPenDown) {
            pathD += `M ${x1} ${y}`;
            fillD += `M ${x1} ${capToY(0)} L ${x1} ${y}`;
            isPenDown = true;
        } else {
            pathD += ` L ${x1} ${y}`;
            fillD += ` L ${x1} ${y}`;
        }
        
        pathD += ` L ${x2} ${y}`;
        fillD += ` L ${x2} ${y}`;
    }
    
    if (isPenDown) {
        fillD += ` L ${timeToX(profile[profile.length-1].end)} ${capToY(0)} Z`;
    }
    
    if (fillD.trim() !== '') {
        // 3. Draw Fill under the curve
        const fillPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        fillPath.setAttribute('d', fillD);
        fillPath.setAttribute('class', 'cumul-fill');
        cumulSvgEl.appendChild(fillPath);
        
        // 4. Draw The main step-line curve
        const strokePath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        strokePath.setAttribute('d', pathD);
        strokePath.setAttribute('class', 'cumul-path');
        cumulSvgEl.appendChild(strokePath);
    }
    
    // 5. Overlay red lines specifically where the capacity is violated
    profile.forEach((p) => {
        if (p.isDefined && (p.level < state.constraintMin || p.level > state.constraintMax)) {
            const x1 = timeToX(p.start);
            const x2 = timeToX(p.end);
            const y = capToY(p.level);
            const vPath = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            vPath.setAttribute('x1', x1);
            vPath.setAttribute('x2', x2);
            vPath.setAttribute('y1', y);
            vPath.setAttribute('y2', y);
            vPath.setAttribute('class', 'violation-stroke');
            cumulSvgEl.appendChild(vPath);
        }
    });
}

// Start application
init();
