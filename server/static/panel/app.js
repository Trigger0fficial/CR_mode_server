const tokenKey = "crmod-token";

const loginBox = document.querySelector("#login");
const panel = document.querySelector("#panel");
const list = document.querySelector("#list");

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

function modelCard(item) {
  const node = document.createElement("article");
  node.className = "model";
  const sizeMb = (item.size / 1024 / 1024).toFixed(1);
  node.innerHTML = `
    <div>
      <h3></h3>
      <p class="desc"></p>
      <p class="note"></p>
      <span class="badge"></span>
      <p class="meta"></p>
    </div>
    <button type="button"></button>
  `;
  node.querySelector("h3").textContent = item.name;
  node.querySelector(".desc").textContent = item.description || "Без описания";
  node.querySelector(".note").textContent = item.note ? "Примечание: " + item.note : "";
  const badge = node.querySelector(".badge");
  badge.textContent = item.is_active ? "активна" : "выключена";
  badge.classList.add(item.is_active ? "on" : "off");
  const classes = item.labels.length ? item.labels.length + " карт" : "классы не заданы";
  node.querySelector(".meta").textContent = item.filename + " · " + sizeMb + " МБ · " + classes;
  const button = node.querySelector("button");
  button.className = item.is_active ? "warn" : "";
  button.textContent = item.is_active ? "Выключить" : "Сделать активной";
  button.addEventListener("click", async () => {
    button.disabled = true;
    await api("/api/models/" + item.id + "/", {
      method: "PATCH",
      json: { is_active: !item.is_active },
    });
    await load();
  });
  return node;
}

async function load() {
  const items = await api("/api/models/");
  list.replaceChildren();
  if (!items.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "Моделей пока нет.";
    list.append(empty);
    return;
  }
  items.forEach((item) => list.append(modelCard(item)));
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
  showPanel(false);
});

document.querySelector("#upload").addEventListener("submit", async (event) => {
  event.preventDefault();
  const error = document.querySelector("#upload-error");
  error.hidden = true;
  const form = event.target;
  const body = new FormData(form);
  if (!body.get("is_active")) body.set("is_active", "false");
  const labels = body.get("labels_file");
  if (!labels || !labels.size) body.delete("labels_file");
  try {
    await api("/api/models/", { method: "POST", body });
    form.reset();
    form.querySelector("[name=is_active]").checked = true;
    await load();
  } catch (err) {
    error.textContent = err.message;
    error.hidden = false;
  }
});

if (token()) {
  showPanel(true);
  load().catch(() => {
    localStorage.removeItem(tokenKey);
    showPanel(false);
  });
}
