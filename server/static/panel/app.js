const tokenKey = "crmod-token";

const loginBox = document.querySelector("#login");
const panel = document.querySelector("#panel");
const list = document.querySelector("#list");

let poll = 0;

function token() {
  return localStorage.getItem(tokenKey);
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (token()) headers.set("Authorization", "Token " + token());
  if (options.json) {
    headers.set("Content-Type", "application/json");
    options.body = JSON.stringify(options.json);
  }
  const response = await fetch(path, { ...options, headers });
  const data = response.status === 204 ? null : await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.detail || "Ошибка запроса");
  }
  return data;
}

function showPanel(on) {
  loginBox.hidden = on;
  panel.hidden = !on;
}

const stateText = {
  pending: "сервер готовит файл для телефона…",
  ready: "готова к работе",
  failed: "конвертация не удалась",
};

function modelCard(item) {
  const node = document.createElement("article");
  node.className = "model";
  node.innerHTML = `
    <div>
      <h3></h3>
      <p class="desc"></p>
      <p class="note"></p>
      <p class="state"></p>
      <p class="err error"></p>
      <span class="badge"></span>
      <p class="meta"></p>
    </div>
    <div class="actions"></div>
  `;
  node.querySelector("h3").textContent = item.name;
  node.querySelector(".desc").textContent = item.description || "Без описания";
  node.querySelector(".note").textContent = item.note ? "Примечание: " + item.note : "";

  const state = node.querySelector(".state");
  state.textContent = stateText[item.status] || item.status;
  state.className = "state " + item.status;

  const err = node.querySelector(".err");
  err.textContent = item.error || "";
  err.hidden = !item.error;

  const badge = node.querySelector(".badge");
  badge.textContent = item.is_active ? "активна" : "выключена";
  badge.classList.add(item.is_active ? "on" : "off");

  const parts = [];
  if (item.filename) parts.push(item.filename);
  if (item.size) parts.push((item.size / 1024 / 1024).toFixed(1) + " МБ");
  if (item.labels.length) parts.push(item.labels.length + " карт");
  if (item.source_filename) parts.push("загружено: " + item.source_filename);
  node.querySelector(".meta").textContent = parts.join(" · ");

  const actions = node.querySelector(".actions");
  if (item.status === "failed") {
    actions.append(action("Конвертировать снова", "", async () => {
      await api("/api/models/" + item.id + "/convert/", { method: "POST" });
      await load();
    }));
  }
  if (item.status === "ready") {
    actions.append(action(
      item.is_active ? "Выключить" : "Сделать активной",
      item.is_active ? "warn" : "",
      async () => {
        await api("/api/models/" + item.id + "/", {
          method: "PATCH",
          json: { is_active: !item.is_active },
        });
        await load();
      },
    ));
  }
  return node;
}

function action(label, className, run) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  if (className) button.className = className;
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      await run();
    } finally {
      button.disabled = false;
    }
  });
  return button;
}

async function load() {
  const items = await api("/api/models/");
  list.replaceChildren();
  if (!items.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "Моделей пока нет.";
    list.append(empty);
  } else {
    items.forEach((item) => list.append(modelCard(item)));
  }
  // Пока хоть одна модель конвертируется, обновляем список сами.
  clearTimeout(poll);
  if (items.some((item) => item.status === "pending")) {
    poll = setTimeout(() => load().catch(() => {}), 3000);
  }
}

document.querySelector("#login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const error = document.querySelector("#login-error");
  error.hidden = true;
  const body = new FormData(event.target);
  try {
    const data = await api("/api/auth/login/", {
      method: "POST",
      json: { email: body.get("email"), password: body.get("password") },
    });
    localStorage.setItem(tokenKey, data.token);
    showPanel(true);
    await load();
  } catch (err) {
    error.textContent = err.message;
    error.hidden = false;
  }
});

document.querySelector("#logout").addEventListener("click", () => {
  localStorage.removeItem(tokenKey);
  clearTimeout(poll);
  showPanel(false);
});

document.querySelector("#upload").addEventListener("submit", async (event) => {
  event.preventDefault();
  const error = document.querySelector("#upload-error");
  error.hidden = true;
  const form = event.target;
  const submit = form.querySelector("button[type=submit]");
  const body = new FormData(form);
  if (!body.get("is_active")) body.set("is_active", "false");
  submit.disabled = true;
  submit.textContent = "Загружаю…";
  try {
    await api("/api/models/", { method: "POST", body });
    form.reset();
    form.querySelector("[name=is_active]").checked = true;
    await load();
  } catch (err) {
    error.textContent = err.message;
    error.hidden = false;
  } finally {
    submit.disabled = false;
    submit.textContent = "Загрузить";
  }
});

if (token()) {
  showPanel(true);
  load().catch(() => {
    localStorage.removeItem(tokenKey);
    showPanel(false);
  });
}
