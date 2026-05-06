(function(){
  const STORAGE='techWorklog';
  const WORK_MIN = 8*60 + 48; // 8h48

  // ----- Períodos -----
  const startOfDay = (d)=>{ const x=new Date(d); x.setHours(0,0,0,0); return x.getTime(); };
  const endOfDay   = (d)=>{ const x=new Date(d); x.setHours(23,59,59,999); return x.getTime(); };
  function weekRange(d){
    const x=new Date(d); const day=(x.getDay()+6)%7;
    const s=new Date(x); s.setDate(x.getDate()-day); s.setHours(0,0,0,0);
    const e=new Date(s); e.setDate(s.getDate()+6); e.setHours(23,59,59,999);
    return [s.getTime(), e.getTime()];
  }
  function monthRange(d){
    const x=new Date(d);
    const s=new Date(x.getFullYear(), x.getMonth(), 1, 0,0,0,0);
    const e=new Date(x.getFullYear(), x.getMonth()+1, 0, 23,59,59,999);
    return [s.getTime(), e.getTime()];
  }
  function rangeFor(sel){
    const now = Date.now();
    if (sel==='semana') return weekRange(now);
    if (sel==='mes')    return monthRange(now);
    return [startOfDay(now), endOfDay(now)]; // hoje
  }

  // ----- Dados -----
  function loadLog(){
    try { return JSON.parse(localStorage.getItem(STORAGE) || '[]'); }
    catch(e){ return []; }
  }

  function aggregate(periodSel){
    const [ini,fim] = rangeFor(periodSel);
    const rows = loadLog().filter(r => Number(r.end||0) >= ini && Number(r.end||0) <= fim);

    const map = new Map();
    for (const r of rows){
      const tech = String(r.tech||'').trim();
      if (!tech) continue;
      const mins = Math.max(1, Math.round((Number(r.end||0)-Number(r.start||0))/60000));
      if (!map.has(tech)) map.set(tech, {label:tech, mins:0, count:0});
      const obj = map.get(tech);
      obj.mins += mins;
      obj.count += 1;
    }
    // retorna somente quem tem horas
    return Array.from(map.values()).filter(x => x.mins > 0);
  }

  // ----- UI -----
  function pct(mins){ return Math.round((mins/WORK_MIN)*100); }
  function donutColors(p){
    if (p >= 80) return ['rgba(34,197,94,0.9)','rgba(34,197,94,0.15)'];
    if (p >= 50) return ['rgba(234,179,8,0.9)','rgba(234,179,8,0.15)'];
    return ['rgba(239,68,68,0.9)','rgba(239,68,68,0.15)'];
  }

  function renderCards(){
    const sel = document.getElementById('sel-dia')?.value || 'hoje';
    const data = aggregate(sel);
    const grid = document.getElementById('tech-grid');
    if (!grid) return;
    const scrollY = window.scrollY;

    grid.innerHTML = '';
    data.sort((a,b)=> (pct(b.mins) - pct(a.mins)) || a.label.localeCompare(b.label));

    for (const it of data){
      const p = Math.min(100, Math.max(0, pct(it.mins)));
      const colors = donutColors(p);

      const card = document.createElement('div');
      card.className = 'tcard';
      card.innerHTML = `
        <header class="center">
          <div class="name">${it.label||'—'}</div>
          <div class="os-pill">OS: <strong>${it.count}</strong></div>
        </header>
        <div class="body">
          <div class="donut-wrap">
            <canvas class="donut"></canvas>
            <div class="donut-center">
              <div class="big">${p}%</div>
              <div class="sub">de 8h48</div>
            </div>
          </div>
        </div>
      `;
      grid.appendChild(card);

      const ctx = card.querySelector('canvas').getContext('2d');
      new Chart(ctx, {
        type: 'doughnut',
        data: { datasets: [{ data: [p, 100-p], backgroundColor: colors, borderWidth:0 }] },
        options: { responsive:true, cutout: '70%', plugins: { legend:{display:false}, tooltip:{enabled:false} } }
      });
    }
    window.scrollTo({ top: scrollY });
  }

  // ----- CSV -----
  function formatDateYMD(ts) {
    const d = new Date(ts);
    const y = d.getFullYear();
    const m = String(d.getMonth()+1).padStart(2,'0');
    const dd = String(d.getDate()).padStart(2,'0');
    return `${y}-${m}-${dd}`;
  }
  function exportCSV(){
    const sel = document.getElementById('sel-dia')?.value || 'hoje';
    const rows = aggregate(sel);
    const header = ['Tecnico','Minutos','Horas','Percentual'];
    const toLine = (arr)=>arr.map(v=>`"${String(v).replace(/"/g,'""')}"`).join(',');
    const lines = [toLine(header)];
    for (const r of rows){
      const mins = r.mins;
      const horas = (mins/60).toFixed(2);
      const perc = Math.round((mins/WORK_MIN)*100) + '%';
      lines.push(toLine([r.label, mins, horas, perc]));
    }
    const blob = new Blob([lines.join('\n')], {type:'text/csv;charset=utf-8;'});
    const a = document.createElement('a');
    const selTxt = (sel==='hoje' ? '1dia' : sel==='semana' ? '1semana' : 'mensal');
    a.download = `horas_${formatDateYMD(Date.now())}_${selTxt}.csv`;
    a.href = URL.createObjectURL(blob);
    document.body.appendChild(a);
    a.click();
    setTimeout(()=>{ URL.revokeObjectURL(a.href); a.remove(); }, 0);
  }

  // ----- Auto-update -----
  let lastSig = null;
  let pollTimer = null;
  function setPoll(ms){ if(pollTimer) clearInterval(pollTimer); pollTimer = setInterval(()=>{ const sig = signature(); if (sig !== lastSig){ lastSig = sig; triggerRender(); } }, ms); }
  function signature(){
    const raw = localStorage.getItem(STORAGE) || '';
    return raw.length + ':' + raw.slice(-16);
  }
  function triggerRender(){ renderCards(); }

  document.addEventListener('DOMContentLoaded', ()=>{
    document.getElementById('sel-dia')?.addEventListener('change', renderCards);
    document.getElementById('btn-export-csv')?.addEventListener('click', exportCSV);

    // primeiro render
    lastSig = signature();
    renderCards();

    // evento de outra aba
    window.addEventListener('storage', (e)=>{ if (e.key===STORAGE){ lastSig = signature(); triggerRender(); } });

    // polling leve (1.5s)
    const selPoll = document.getElementById('sel-poll');
    const ms = parseInt(selPoll?.value||'3000',10);
    setPoll(ms);
// ao voltar foco
    document.addEventListener('visibilitychange', ()=>{
      if (!document.hidden){
        const sig = signature();
        if (sig !== lastSig){ lastSig = sig; triggerRender(); }
      }
    });
  });
})();