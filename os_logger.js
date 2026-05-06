(function(){
  const STORAGE = 'techWorklog';
  const loggedKey = (machine, endTs)=> `logged:${machine}:${endTs}`;

  function parseTimestamp(txt){
    const m = txt.match(/(\d{2})\/(\d{2})\/(\d{4}),\s*(\d{2}):(\d{2}):(\d{2})/);
    if(!m) return 0;
    const [_,d,mo,y,h,mi,s]=m.map(Number);
    return new Date(y, mo-1, d, h, mi, s).getTime();
  }

  function snapshot(){
    const cards = Array.from(document.querySelectorAll('.card, .os-card, .machine-card'));
    cards.forEach(card=>{
      // status via dataset (se disponível)
      const status = (card.dataset.status || card.innerText.toLowerCase().includes('encerrada') && 'encerrada') || '';
      if (status !== 'encerrada') return;

      const machine = (card.querySelector('h3, strong, .code, .title, .machine')?.textContent || '').trim();
      const techDs  = (card.dataset.tecnico || '').trim();
      const startDs = Number(card.dataset.aceitaTs || 0);
      const endDs   = Number(card.dataset.encerraTs || 0);

      let tech = techDs;
      if (!tech){
        const lineTech = Array.from(card.querySelectorAll('*')).find(n=>/técnico.*fechamento/i.test(n.textContent));
        tech = lineTech ? lineTech.textContent.replace(/.*fechamento[:\s]*/i,'').trim() : '';
      }
      let startTs = startDs;
      let endTs   = endDs;
      if (!endTs){
        const acceptedNode = Array.from(card.querySelectorAll('*')).find(n=>/aceita[:]/i.test(n.textContent));
        const closedNode   = Array.from(card.querySelectorAll('*')).find(n=>/encerrad[ao][:]/i.test(n.textContent));
        startTs = startTs || parseTimestamp(acceptedNode ? acceptedNode.textContent : '');
        endTs   = endTs   || parseTimestamp(closedNode ? closedNode.textContent : '');
      }
      if (!endTs || !tech) return;

      const lk = loggedKey(machine || '---', endTs);
      if (localStorage.getItem(lk)) return;

      const arr = JSON.parse(localStorage.getItem(STORAGE) || '[]');
      arr.push({ tech, start: startTs || endTs, end: endTs, machine: machine || '' });
      localStorage.setItem(STORAGE, JSON.stringify(arr));
      localStorage.setItem(lk, '1');
      try { document.dispatchEvent(new Event('techWorklogUpdated')); } catch(e) {}
      try { window.dispatchEvent(new StorageEvent('storage', { key: STORAGE, newValue: JSON.stringify(arr) })); } catch(e) {}
    });
  }

  snapshot();
  const obs = new MutationObserver(()=> setTimeout(snapshot, 150));
  obs.observe(document.body, { childList:true, subtree:true });
})();