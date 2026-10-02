"use strict";

/*
 * タスク管理アプリ 画面側ロジック(表示専用・ダミーデータ版)
 *
 * このファイルはサーバー(server/app.js)・データベースとまだ接続していません。
 * データは localStorage に保存したダミーストアで代用しています。
 * サーバー実装後は、`Store` の各関数を fetch() によるAPI呼び出しに
 * 置き換える想定です(置き換え箇所は "TODO(API)" で示しています)。
 *
 * 画面の仕様は docs/requirements.md 5章、docs/use-cases.md を参照。
 */

// ===========================================================================
// ダミーデータストア(TODO(API): server/app.js のAPIに置き換える)
// ===========================================================================
const Store = (() => {
  const DB_KEY = "tma_demo_db";
  const SESSION_KEY = "tma_demo_session";
  const MAX_COLUMNS = 10;
  const DEFAULT_COLUMN_NAMES = ["未着手", "進行中", "完了"];

  function loadDb() {
    try {
      const raw = localStorage.getItem(DB_KEY);
      return raw ? JSON.parse(raw) : { users: {} };
    } catch {
      return { users: {} };
    }
  }

  function saveDb(db) {
    try {
      localStorage.setItem(DB_KEY, JSON.stringify(db));
    } catch {
      /* 保存できない場合は何もしない(容量超過など) */
    }
  }

  function nextId(prefix) {
    return prefix + "-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 7);
  }

  // --- セッション(ログイン状態) ---
  function getSession() {
    try {
      const raw = sessionStorage.getItem(SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  function setSession(username) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({ username }));
  }

  function clearSession() {
    sessionStorage.removeItem(SESSION_KEY);
  }

  // --- アカウント ---
  // 注意: これはダミー実装のため、パスワードを平文で比較しています。
  // 実装時(server/app.js)では bcrypt でハッシュ化して保存・照合します(要件定義書 L-5)。
  function register(username, password) {
    username = (username || "").trim();
    const db = loadDb();
    if (!username) return { ok: false, error: "ユーザー名を入力してください。" };
    if (!password) return { ok: false, error: "パスワードを入力してください。" };
    if (db.users[username]) return { ok: false, error: "そのユーザー名はすでに使われています。" };

    db.users[username] = {
      password,
      board: {
        columns: DEFAULT_COLUMN_NAMES.map((name) => ({
          id: nextId("col"),
          name,
          cards: [],
        })),
      },
    };
    saveDb(db);
    setSession(username);
    return { ok: true };
  }

  function login(username, password) {
    username = (username || "").trim();
    const db = loadDb();
    const user = db.users[username];
    if (!user || user.password !== password) {
      return { ok: false, error: "ユーザー名またはパスワードが正しくありません。" };
    }
    setSession(username);
    return { ok: true };
  }

  function logout() {
    clearSession();
  }

  function currentUsername() {
    const session = getSession();
    return session ? session.username : null;
  }

  // --- ボード(列・カード) ---
  function getBoard() {
    const username = currentUsername();
    if (!username) return null;
    const db = loadDb();
    const user = db.users[username];
    return user ? user.board : null;
  }

  function saveBoard(board) {
    const username = currentUsername();
    if (!username) return;
    const db = loadDb();
    if (!db.users[username]) return;
    db.users[username].board = board;
    saveDb(db);
  }

  function addColumn(name) {
    const board = getBoard();
    if (!board) return { ok: false, error: "ログインしていません。" };
    if (board.columns.length >= MAX_COLUMNS) {
      return { ok: false, error: `列は最大${MAX_COLUMNS}列までです。` };
    }
    board.columns.push({ id: nextId("col"), name, cards: [] });
    saveBoard(board);
    return { ok: true };
  }

  function deleteColumn(columnId) {
    const board = getBoard();
    if (!board) return;
    board.columns = board.columns.filter((c) => c.id !== columnId);
    saveBoard(board);
  }

  function addCard(columnId, { title, description, dueDate, priority }) {
    const board = getBoard();
    if (!board) return { ok: false, error: "ログインしていません。" };
    const column = board.columns.find((c) => c.id === columnId);
    if (!column) return { ok: false, error: "列が見つかりません。" };
    column.cards.push({
      id: nextId("card"),
      title,
      description: description || "",
      dueDate: dueDate || "",
      priority: priority || "",
      createdAt: new Date().toISOString(),
    });
    saveBoard(board);
    return { ok: true };
  }

  function updateCard(cardId, { title, description, dueDate, priority }) {
    const board = getBoard();
    if (!board) return;
    for (const column of board.columns) {
      const card = column.cards.find((c) => c.id === cardId);
      if (card) {
        card.title = title;
        card.description = description || "";
        card.dueDate = dueDate || "";
        card.priority = priority || "";
        break;
      }
    }
    saveBoard(board);
  }

  function deleteCard(cardId) {
    const board = getBoard();
    if (!board) return;
    for (const column of board.columns) {
      column.cards = column.cards.filter((c) => c.id !== cardId);
    }
    saveBoard(board);
  }

  /** カードを移動・並び替えする(要件定義書 T-4)。
   *  同じ列内への移動であれば、自由な並び替えになる。
   *  insertIndex を省略した場合は、列の末尾に追加する。 */
  function moveCard(cardId, targetColumnId, insertIndex) {
    const board = getBoard();
    if (!board) return;
    let moving = null;
    for (const column of board.columns) {
      const idx = column.cards.findIndex((c) => c.id === cardId);
      if (idx !== -1) {
        moving = column.cards.splice(idx, 1)[0];
        break;
      }
    }
    if (!moving) return;
    const target = board.columns.find((c) => c.id === targetColumnId);
    if (!target) return;
    const idx =
      typeof insertIndex === "number" && insertIndex >= 0 && insertIndex <= target.cards.length
        ? insertIndex
        : target.cards.length;
    target.cards.splice(idx, 0, moving);
    saveBoard(board);
  }

  // 並び替えボタン(優先度順・期限順)で使う優先度の順位。数字が小さいほど先頭。
  const PRIORITY_RANK = { high: 0, medium: 1, low: 2 };

  /** 列内のカードを重要度順(重→中→低→未設定)に並び替える(要件定義書 T-5)。
   *  Array.prototype.sort は安定ソートのため、重要度が同じカード同士は
   *  並び替え前の順序(自由な並び替えの結果)を保つ。 */
  function sortColumnByPriority(columnId) {
    const board = getBoard();
    if (!board) return;
    const column = board.columns.find((c) => c.id === columnId);
    if (!column) return;
    column.cards.sort((a, b) => {
      const ra = a.priority ? PRIORITY_RANK[a.priority] : 3;
      const rb = b.priority ? PRIORITY_RANK[b.priority] : 3;
      return ra - rb;
    });
    saveBoard(board);
  }

  /** 列内のカードを期限が近い順に並び替える(未設定は最後)(要件定義書 T-5)。 */
  function sortColumnByDueDate(columnId) {
    const board = getBoard();
    if (!board) return;
    const column = board.columns.find((c) => c.id === columnId);
    if (!column) return;
    column.cards.sort((a, b) => {
      if (!a.dueDate && !b.dueDate) return 0;
      if (!a.dueDate) return 1;
      if (!b.dueDate) return -1;
      return a.dueDate < b.dueDate ? -1 : a.dueDate > b.dueDate ? 1 : 0;
    });
    saveBoard(board);
  }

  return {
    register,
    login,
    logout,
    currentUsername,
    getBoard,
    addColumn,
    deleteColumn,
    addCard,
    updateCard,
    deleteCard,
    moveCard,
    sortColumnByPriority,
    sortColumnByDueDate,
    MAX_COLUMNS,
  };
})();

// ===========================================================================
// 共通ユーティリティ
// ===========================================================================

/** 期限切れかどうかを判定する(要件定義書 D-1〜D-3)。
 *  「完了」列のカードは期限切れでも強調表示しない。 */
function isOverdue(card, columnName) {
  if (!card.dueDate) return false;
  if (columnName === "完了") return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const due = new Date(card.dueDate + "T00:00:00");
  return due < today;
}

const PRIORITY_LABELS = { high: "重", medium: "中", low: "低" };

function priorityLabel(priority) {
  return PRIORITY_LABELS[priority] || "";
}

function formatDate(isoDateOrDateTime) {
  if (!isoDateOrDateTime) return "";
  const d = new Date(isoDateOrDateTime);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}`;
}

function formatDateTime(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n) => String(n).padStart(2, "0");
  return `${formatDate(iso)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function showToast(message) {
  const toast = document.getElementById("toast");
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => toast.classList.remove("show"), 2000);
}

function setFieldError(fieldEl, message) {
  if (message) {
    fieldEl.classList.add("has-error");
    const err = fieldEl.querySelector(".error");
    if (err) err.textContent = message;
  } else {
    fieldEl.classList.remove("has-error");
  }
}

// ===========================================================================
// ログイン画面(login.html)
// ===========================================================================
function initLoginPage() {
  const loginForm = document.getElementById("login-form");
  const registerForm = document.getElementById("register-form");
  const loginError = document.getElementById("login-error");
  const registerError = document.getElementById("register-error");

  // 既にログイン済みなら、メイン画面へ
  if (Store.currentUsername()) {
    window.location.href = "index.html";
    return;
  }

  document.getElementById("go-register").addEventListener("click", () => {
    loginForm.hidden = true;
    registerForm.hidden = false;
  });
  document.getElementById("go-login").addEventListener("click", () => {
    registerForm.hidden = true;
    loginForm.hidden = false;
  });

  loginForm.addEventListener("submit", (e) => {
    e.preventDefault();
    loginError.classList.remove("show");
    const username = document.getElementById("login-username").value;
    const password = document.getElementById("login-password").value;
    const result = Store.login(username, password); // TODO(API): POST /api/login
    if (result.ok) {
      window.location.href = "index.html";
    } else {
      loginError.textContent = result.error;
      loginError.classList.add("show");
    }
  });

  registerForm.addEventListener("submit", (e) => {
    e.preventDefault();
    registerError.classList.remove("show");
    const username = document.getElementById("register-username").value;
    const password = document.getElementById("register-password").value;
    const password2 = document.getElementById("register-password2").value;

    if (password !== password2) {
      registerError.textContent = "パスワードが一致しません。";
      registerError.classList.add("show");
      return;
    }

    const result = Store.register(username, password); // TODO(API): POST /api/register
    if (result.ok) {
      window.location.href = "index.html";
    } else {
      registerError.textContent = result.error;
      registerError.classList.add("show");
    }
  });
}

// ===========================================================================
// メイン画面(index.html)
// ===========================================================================
function initBoardPage() {
  // 未ログインなら、ログイン画面へ
  const username = Store.currentUsername();
  if (!username) {
    window.location.href = "login.html";
    return;
  }
  document.getElementById("current-username").textContent = username;

  const boardEl = document.getElementById("board");

  // --- カード追加・編集ダイアログ ---
  const cardDialog = document.getElementById("card-dialog");
  const cardForm = document.getElementById("card-form");
  const cardTitleField = document.getElementById("card-title-field");
  const cardDescField = document.getElementById("card-description-field");
  const cardTitleInput = document.getElementById("card-title");
  const cardDescInput = document.getElementById("card-description");
  const cardDueInput = document.getElementById("card-due-date");
  const cardCreatedAt = document.getElementById("card-created-at");
  const cardDeleteBtn = document.getElementById("card-delete-btn");
  const cardPriorityInput = document.getElementById("card-priority");
  const priorityOptions = Array.from(document.querySelectorAll(".priority-option"));
  let editingCardId = null;
  let addingToColumnId = null;

  function setSelectedPriority(priority) {
    cardPriorityInput.value = priority || "";
    priorityOptions.forEach((btn) => {
      btn.classList.toggle("selected", btn.dataset.priority === (priority || ""));
    });
  }

  priorityOptions.forEach((btn) => {
    btn.addEventListener("click", () => setSelectedPriority(btn.dataset.priority));
  });

  function openAddCardDialog(columnId) {
    editingCardId = null;
    addingToColumnId = columnId;
    document.getElementById("card-dialog-title").textContent = "タスクを追加";
    cardForm.reset();
    setFieldError(cardTitleField, "");
    setFieldError(cardDescField, "");
    setSelectedPriority("");
    cardCreatedAt.textContent = "";
    cardDeleteBtn.hidden = true;
    cardDialog.showModal();
    cardTitleInput.focus();
  }

  function openEditCardDialog(card) {
    editingCardId = card.id;
    addingToColumnId = null;
    document.getElementById("card-dialog-title").textContent = "タスクを編集";
    setFieldError(cardTitleField, "");
    setFieldError(cardDescField, "");
    cardTitleInput.value = card.title;
    cardDescInput.value = card.description || "";
    cardDueInput.value = card.dueDate || "";
    setSelectedPriority(card.priority || "");
    cardCreatedAt.textContent = `作成日時: ${formatDateTime(card.createdAt)}(変更できません)`;
    cardDeleteBtn.hidden = false;
    cardDialog.showModal();
    cardTitleInput.focus();
  }

  document.getElementById("card-cancel-btn").addEventListener("click", () => cardDialog.close());

  cardForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const title = cardTitleInput.value.trim();
    const description = cardDescInput.value;
    const dueDate = cardDueInput.value;
    const priority = cardPriorityInput.value;

    let hasError = false;
    if (!title || title.length > 50) {
      setFieldError(cardTitleField, "タイトルを入力してください(1〜50文字)。");
      hasError = true;
    } else {
      setFieldError(cardTitleField, "");
    }
    if (description.length > 500) {
      setFieldError(cardDescField, "詳細説明文は500文字までです。");
      hasError = true;
    } else {
      setFieldError(cardDescField, "");
    }
    if (hasError) return;

    if (editingCardId) {
      Store.updateCard(editingCardId, { title, description, dueDate, priority }); // TODO(API): PUT /api/cards/:id
      showToast("タスクを更新しました。");
    } else {
      const result = Store.addCard(addingToColumnId, { title, description, dueDate, priority }); // TODO(API): POST /api/cards
      if (!result.ok) {
        showToast(result.error);
        return;
      }
      showToast("タスクを追加しました。");
    }
    cardDialog.close();
    renderBoard();
  });

  // --- カード削除確認ダイアログ ---
  const cardDeleteDialog = document.getElementById("card-delete-dialog");
  let cardIdToDelete = null;

  cardDeleteBtn.addEventListener("click", () => {
    cardIdToDelete = editingCardId;
    document.getElementById("card-delete-message").textContent =
      `「${cardTitleInput.value.trim()}」を削除します。削除後は元に戻せません。`;
    cardDialog.close();
    cardDeleteDialog.showModal();
  });
  document.getElementById("card-delete-cancel-btn").addEventListener("click", () => {
    cardDeleteDialog.close();
  });
  document.getElementById("card-delete-confirm-btn").addEventListener("click", () => {
    Store.deleteCard(cardIdToDelete); // TODO(API): DELETE /api/cards/:id
    cardDeleteDialog.close();
    showToast("タスクを削除しました。");
    renderBoard();
  });

  // --- 列追加ダイアログ ---
  const columnDialog = document.getElementById("column-dialog");
  const columnForm = document.getElementById("column-form");
  const columnNameField = document.getElementById("column-name-field");
  const columnNameInput = document.getElementById("column-name");

  document.getElementById("column-cancel-btn").addEventListener("click", () => columnDialog.close());

  columnForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const name = columnNameInput.value.trim();
    if (!name || name.length > 20) {
      setFieldError(columnNameField, "列名を入力してください(1〜20文字)。");
      return;
    }
    const result = Store.addColumn(name); // TODO(API): POST /api/columns
    if (!result.ok) {
      setFieldError(columnNameField, result.error);
      return;
    }
    setFieldError(columnNameField, "");
    columnDialog.close();
    showToast("列を追加しました。");
    renderBoard();
  });

  // --- 列削除確認ダイアログ ---
  const columnDeleteDialog = document.getElementById("column-delete-dialog");
  let columnIdToDelete = null;

  document.getElementById("column-delete-cancel-btn").addEventListener("click", () => {
    columnDeleteDialog.close();
  });
  document.getElementById("column-delete-confirm-btn").addEventListener("click", () => {
    Store.deleteColumn(columnIdToDelete); // TODO(API): DELETE /api/columns/:id
    columnDeleteDialog.close();
    showToast("列を削除しました。");
    renderBoard();
  });

  // --- ログアウト ---
  document.getElementById("logout-btn").addEventListener("click", () => {
    Store.logout(); // TODO(API): POST /api/logout
    window.location.href = "login.html";
  });

  // --- 描画 ---
  function renderBoard() {
    const board = Store.getBoard();
    boardEl.innerHTML = "";
    if (!board) return;

    board.columns.forEach((column) => {
      const colEl = document.createElement("div");
      colEl.className = "column";
      colEl.dataset.columnId = column.id;

      const head = document.createElement("div");
      head.className = "col-head";
      head.innerHTML = `<span></span><button class="col-menu-btn" title="列のメニュー">︙</button>`;
      head.querySelector("span").textContent = column.name;
      head.querySelector(".col-menu-btn").addEventListener("click", () => {
        columnIdToDelete = column.id;
        const msg = column.cards.length > 0
          ? `「${column.name}」を削除します。列の中の${column.cards.length}件のカードも一緒に削除されます。`
          : `「${column.name}」を削除します。`;
        document.getElementById("column-delete-message").textContent = msg;
        columnDeleteDialog.showModal();
      });
      colEl.appendChild(head);

      // 並び替えボタン(優先度順・期限順)(要件定義書 T-5)
      const sortRow = document.createElement("div");
      sortRow.className = "col-sort";
      const sortByPriorityBtn = document.createElement("button");
      sortByPriorityBtn.type = "button";
      sortByPriorityBtn.className = "sort-btn";
      sortByPriorityBtn.textContent = "優先度順";
      sortByPriorityBtn.addEventListener("click", () => {
        Store.sortColumnByPriority(column.id); // TODO(API): PUT /api/columns/:id/sort?by=priority
        renderBoard();
      });
      const sortByDueBtn = document.createElement("button");
      sortByDueBtn.type = "button";
      sortByDueBtn.className = "sort-btn";
      sortByDueBtn.textContent = "期限順";
      sortByDueBtn.addEventListener("click", () => {
        Store.sortColumnByDueDate(column.id); // TODO(API): PUT /api/columns/:id/sort?by=due
        renderBoard();
      });
      sortRow.appendChild(sortByPriorityBtn);
      sortRow.appendChild(sortByDueBtn);
      colEl.appendChild(sortRow);

      const listEl = document.createElement("div");
      listEl.className = "card-list";

      /** ドロップ先のカード一覧の中で、マウスのY座標から挿入位置を求める。
       *  ドラッグ中のカード自身は計算対象から除く(自由な並び替え時に位置がずれるため)。 */
      function calcInsertIndex(clientY, draggingCardId) {
        const cardEls = Array.from(listEl.querySelectorAll(".card")).filter(
          (el) => el.dataset.cardId !== draggingCardId
        );
        for (let i = 0; i < cardEls.length; i++) {
          const rect = cardEls[i].getBoundingClientRect();
          if (clientY < rect.top + rect.height / 2) {
            const cardId = cardEls[i].dataset.cardId;
            return column.cards.findIndex((c) => c.id === cardId);
          }
        }
        return column.cards.length;
      }

      listEl.addEventListener("dragover", (e) => {
        e.preventDefault();
        colEl.classList.add("drag-over");
      });
      listEl.addEventListener("dragleave", () => colEl.classList.remove("drag-over"));
      listEl.addEventListener("drop", (e) => {
        e.preventDefault();
        colEl.classList.remove("drag-over");
        const cardId = e.dataTransfer.getData("text/plain");
        if (cardId) {
          const insertIndex = calcInsertIndex(e.clientY, cardId);
          Store.moveCard(cardId, column.id, insertIndex); // TODO(API): PUT /api/cards/:id/move
          renderBoard();
        }
      });

      column.cards.forEach((card) => {
        const cardEl = document.createElement("div");
        cardEl.className = "card" + (isOverdue(card, column.name) ? " overdue" : "");
        cardEl.draggable = true;
        cardEl.dataset.cardId = card.id;

        const metaText = card.dueDate
          ? `期限: ${formatDate(card.dueDate)}` + (isOverdue(card, column.name) ? "(期限切れ)" : "")
          : "期限: なし";

        const badgeHtml = card.priority
          ? `<span class="priority-badge priority-${card.priority}"></span>`
          : "";
        cardEl.innerHTML = `${badgeHtml}<div class="title"></div><div class="meta"></div>`;
        if (card.priority) {
          cardEl.querySelector(".priority-badge").textContent = priorityLabel(card.priority);
        }
        cardEl.querySelector(".title").textContent = card.title;
        cardEl.querySelector(".meta").textContent = metaText;

        cardEl.addEventListener("click", () => openEditCardDialog(card));
        cardEl.addEventListener("dragstart", (e) => {
          e.dataTransfer.setData("text/plain", card.id);
          cardEl.classList.add("dragging");
        });
        cardEl.addEventListener("dragend", () => cardEl.classList.remove("dragging"));

        listEl.appendChild(cardEl);
      });

      colEl.appendChild(listEl);

      const addBtn = document.createElement("button");
      addBtn.className = "btn-add";
      addBtn.textContent = "＋ タスク追加";
      addBtn.addEventListener("click", () => openAddCardDialog(column.id));
      colEl.appendChild(addBtn);

      boardEl.appendChild(colEl);
    });

    // 列追加ボタン
    if (board.columns.length < Store.MAX_COLUMNS) {
      const addColEl = document.createElement("div");
      addColEl.className = "add-column";
      const btn = document.createElement("button");
      btn.textContent = "＋ 列を追加";
      btn.addEventListener("click", () => {
        columnForm.reset();
        setFieldError(columnNameField, "");
        columnDialog.showModal();
        columnNameInput.focus();
      });
      addColEl.appendChild(btn);
      boardEl.appendChild(addColEl);
    }
  }

  renderBoard();
}

// ===========================================================================
// エントリーポイント(ページごとに初期化を振り分け)
// ===========================================================================
document.addEventListener("DOMContentLoaded", () => {
  if (document.getElementById("board")) {
    initBoardPage();
  } else if (document.getElementById("login-form")) {
    initLoginPage();
  }
});
