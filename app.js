import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getFirestore, collection, query, where, orderBy, onSnapshot,
  doc, updateDoc, deleteDoc, serverTimestamp, getDocs, limit, startAfter
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyA3evRNrOz-Gm2Zin9643crKb0GQ6lMWdQ",
  authDomain: "rohdenos.firebaseapp.com",
  projectId: "rohdenos",
  storageBucket: "rohdenos.firebasestorage.app",
  messagingSenderId: "838154731625",
  appId: "1:838154731625:web:4d84096a389683b098a2f8",
  measurementId: "G-7VLYE6DYJH"
};

const app = initializeApp(firebaseConfig);
const db  = getFirestore(app);

const cardsContainer = document.getElementById("cards");
const selStatus = document.getElementById("filter-status");
const selSetor  = document.getElementById("filter-sector");

let unsubscribe = null;

function toMillis(ts) {
  if (!ts) return 0;
  if (typeof ts === "number") return ts;
  if (ts.toMillis) return ts.toMillis();
  if (ts.toDate) return ts.toDate().getTime();
  try { return new Date(ts).getTime() || 0; } catch { return 0; }
}
function fmt(ts) {
  if (!ts) return "—";
  const d = (ts?.toDate ? ts.toDate() : (ts instanceof Date ? ts : new Date(ts)));
  return d.toLocaleString("pt-BR", { hour12: false });
}
function normalizeStatusLabel(label) {
  const s = String(label || "").toLowerCase();
  if (s.includes("abert"))  return "aberta";
  if (s.includes("aceit"))  return "aceita";
  if (s.includes("encerr")) return "encerrada";
  if (s.includes("arquiv")) return "arquivadas";
  return "todas";
}

function dedupeByMachine(items) {
  const groups = new Map();
  for (const it of items) {
    const key = (it.machine || it.maquina || "").trim().toUpperCase();
    if (!key) continue;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(it);
  }
  const score = (x) => (x.isFromApp ? 1e15 : 0) + toMillis(x.receivedAt);
  const result = [];
  for (const arr of groups.values()) {
    arr.sort((a,b) => score(b) - score(a));
    const keep = arr[0];
    keep._dups = arr.slice(1).map(d => d.id);
    result.push(keep);
  }
  const noMachine = items.filter(i => !((i.machine || i.maquina || "").trim()));
  for (const it of noMachine) it._dups = [];
  return [...result, ...noMachine];
}

function attachListener() {
  if (unsubscribe) { unsubscribe(); unsubscribe = null; }
  const statusSel = normalizeStatusLabel(selStatus.value);
  const setorSel  = selSetor.value;

  const constraints = [];
  if (setorSel === "1" || setorSel === "2") constraints.push(where("setor", "==", Number(setorSel)));
  if (["aberta","aceita","encerrada"].includes(statusSel)) constraints.push(where("status","==",statusSel));
  if (statusSel === "arquivadas") constraints.push(where("archived","==",true));
  else constraints.push(where("archived","==",false));
  constraints.push(orderBy("receivedAt","desc"));

  const q = query(collection(db,"os"), ...constraints);
  unsubscribe = onSnapshot(q, (snap) => {
    const items = [];
    snap.forEach(ds => {
      const d = ds.data();
      items.push({
        id: ds.id,
        isFromApp: d.isFromApp === true,
        nome: d.nome || null,
        machine: d.machine || d.maquina || null,
        tipo: d.tipo || null,
        prioridade: (d.prioridade || "").toString().toUpperCase(),
        setor: (typeof d.setor === "number" ? d.setor : d.sector) ?? null,
        status: (d.status || "").toString(),
        receivedAt: d.receivedAt || null,
        acceptedAt: d.acceptedAt || null,
        closedAt: d.closedAt || null,
        tecnico: d.tecnico || d.tech || null,
        observacoesAbertura: d.observacoesAbertura ?? d.obs ?? d.observacoes ?? null,
        observacoesFechamento: d.observacoesFechamento ?? null,
        archived: !!d.archived
      });
    });
    const deduped = dedupeByMachine(items);
    renderCards(deduped);
    monitorarTempoCartoes();
  }, (err) => {
    console.error(err);
    cardsContainer.innerHTML = `<div class="empty">Erro: ${err.message}</div>`;
  });
}

function renderCards(items) {
  if (!items.length) {
    cardsContainer.innerHTML = `<div class="empty">Nenhuma OS encontrada</div>`;
    return;
  }
  cardsContainer.innerHTML = "";
  for (const it of items) cardsContainer.appendChild(buildCard(it));
}

function buildCard(item) {
  const {
    id, isFromApp, nome, machine, tipo, prioridade,
    setor, status, receivedAt, acceptedAt, closedAt,
    tecnico, observacoesAbertura, observacoesFechamento,
    archived, _dups = []
  } = item;

  const statusLower = (status || "").toLowerCase();
  const statusClass =
    statusLower === "encerrada" ? "st-closed" :
    statusLower === "aceita"    ? "st-accepted" :
                                  "st-open";

  const el = document.createElement("div");
  el.className = "card";
  el.dataset.maquina = (machine || "").toUpperCase();
  el.dataset.prioridade = (prioridade || "P4").toUpperCase();
  el.dataset.status = statusLower;
  el.dataset.horaAbertura = String(toMillis(receivedAt) || Date.now());
  // ✅ Dados extras para o logger (evita parsing de texto)
  el.dataset.aceitaTs = String(toMillis(acceptedAt) || 0);
  el.dataset.encerraTs = String(toMillis(closedAt) || 0);
  el.dataset.tecnico = String(tecnico || '');


  el.innerHTML = `
    <div class="card-header ${statusClass}">
      <div class="title">
        <span class="machine">${machine || "—"}</span>
        <span class="sector">Setor ${setor ?? "—"}</span>
        ${isFromApp ? "" : `<span class="badge">Legado</span>`}
        ${archived ? `<span class="badge badge-archived">Arquivada</span>` : ""}
      </div>
      <div class="actions">
        ${_dups.length > 0 ? `<button class="btn btn-light" data-act="killdups">Excluir duplicatas (${_dups.length})</button>` : ""}
        ${archived
          ? `<button class="btn btn-light" data-act="unarchive">Desarquivar</button>`
          : `<button class="btn btn-outline" data-act="archive">Arquivar</button>`}
        <button class="btn btn-danger" data-act="delete">Excluir</button>
      </div>
    </div>
    <div class="card-body">
      <div class="row"><label>Nome:</label><span>${nome || "—"}</span></div>
      <div class="row"><label>Tipo:</label><span>${tipo || "—"}</span></div>
      <div class="row"><label>Prioridade:</label><span>${prioridade || "—"}</span></div>
      <div class="row"><label>Status:</label><span>${status || "—"}</span></div>
      <div class="row"><label>Recebida:</label><span>${fmt(receivedAt)}</span></div>
      <div class="row"><label>Aceita:</label><span>${fmt(acceptedAt)}</span></div>
      <div class="row"><label>Encerrada:</label><span>${fmt(closedAt)}</span></div>
      <div class="row"><label>Técnico (fechamento):</label><span>${tecnico || "—"}</span></div>
      <div class="row"><label>Observações (abertura):</label><span>${observacoesAbertura || "—"}</span></div>
      <div class="row"><label>Observações (fechamento):</label><span>${observacoesFechamento || "—"}</span></div>
    </div>
  `;

  const btnArchive   = el.querySelector('[data-act="archive"]');
  const btnUnarchive = el.querySelector('[data-act="unarchive"]');
  const btnDelete    = el.querySelector('[data-act="delete"]');
  const btnKillDups  = el.querySelector('[data-act="killdups"]');

  if (btnArchive) btnArchive.addEventListener("click", async () => {
    await updateDoc(doc(db,"os",id), { archived:true, archivedAt: serverTimestamp() });
    el.dataset.status = "arquivada";
    el.classList.remove('blink-red');
  });
  if (btnUnarchive) btnUnarchive.addEventListener("click", async () => {
    await updateDoc(doc(db,"os",id), { archived:false, archivedAt: serverTimestamp() });
  });
  if (btnDelete) btnDelete.addEventListener("click", async () => {
    if (confirm(`Excluir a OS "${machine}"? Essa ação não pode ser desfeita.`)) {
      await deleteDoc(doc(db,"os",id));
    }
  });
  if (btnKillDups) btnKillDups.addEventListener("click", async () => {
    if (!_dups.length) return;
    if (!confirm(`Excluir ${_dups.length} duplicata(s) de "${machine}"?`)) return;
    await Promise.all(_dups.map(dupId => deleteDoc(doc(db,"os",dupId))));
  });

  return el;
}

async function fetchAllOs() {
  const coll = collection(db, "os");
  let q = query(coll, orderBy("receivedAt", "desc"), limit(500));
  let out = [];
  let snap = await getDocs(q);
  while (!snap.empty) {
    out.push(...snap.docs.map(d => ({ id: d.id, ...d.data() })));
    const last = snap.docs[snap.docs.length - 1];
    q = query(coll, orderBy("receivedAt", "desc"), startAfter(last), limit(500));
    snap = await getDocs(q);
  }
  return out;
}
function scoreForKeep(x) {
  const ts = toMillis(x.receivedAt);
  return (x.isFromApp ? 1_000_000_000_000 : 0) + ts;
}
async function deleteDuplicates() {
  const all = await fetchAllOs();
  const groups = new Map();
  for (const x of all) {
    const key = (x.machine || "").trim().toUpperCase();
    if (!key) continue;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(x);
  }
  const toDelete = [];
  for (const arr of groups.values()) {
    if (arr.length <= 1) continue;
    arr.sort((a,b) => scoreForKeep(b) - scoreForKeep(a));
    for (const d of arr.slice(1)) toDelete.push(d.id);
  }
  if (!toDelete.length) return alert("Sem duplicatas para excluir.");
  if (!confirm(`Excluir ${toDelete.length} duplicata(s)?`)) return;
  await Promise.all(toDelete.map(id => deleteDoc(doc(db, "os", id))));
  alert(`Duplicatas excluídas: ${toDelete.length}`);
}
async function deleteAllDocs() {
  const all = await fetchAllOs();
  if (!all.length) return alert("Nada para excluir.");
  if (!confirm(`ATENÇÃO: excluir TODOS os ${all.length} cartões? Esta ação não pode ser desfeita.`)) return;
  await Promise.all(all.map(d => deleteDoc(doc(db, "os", d.id))));
  alert("Coleção limpa.");
}

// ===== MONITOR P/ PISCAR EM VERMELHO =====
function monitorarTempoCartoes() {
  const agora = Date.now();
  document.querySelectorAll('.card').forEach(card => {
    const prioridade   = (card.dataset.prioridade || 'P4').toUpperCase();
    const status       = (card.dataset.status || 'aberta').toLowerCase();
    const horaAbertura = Number(card.dataset.horaAbertura || 0);
    if (!horaAbertura) return;
    if (status !== 'aberta' && status !== 'st-open') {
      card.classList.remove('blink-red');
      return;
    }
    const minutos = (agora - horaAbertura) / 60000;
    const limite = (prioridade === 'P4') ? 2 : 5;
    if (minutos >= limite) card.classList.add('blink-red');
    else card.classList.remove('blink-red');
  });
}
setInterval(monitorarTempoCartoes, 10000);

// eventos
selStatus.addEventListener("change", attachListener);
selSetor.addEventListener("change", attachListener);
document.getElementById("btn-clean-dups")?.addEventListener("click", deleteDuplicates);
document.getElementById("btn-del-all")?.addEventListener("click", deleteAllDocs);

// start
attachListener();
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', monitorarTempoCartoes);
} else {
  monitorarTempoCartoes();
}
