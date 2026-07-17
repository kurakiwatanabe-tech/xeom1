// db.js
// Lưu trữ dữ liệu đơn giản bằng file JSON trên đĩa.
// Phù hợp cho demo / dự án nhỏ. Khi lên production nên thay bằng
// PostgreSQL + PostGIS (cho truy vấn khoảng cách hiệu quả) hoặc MongoDB.

const fs = require('fs');
const path = require('path');

const DB_PATH = path.join(__dirname, 'data', 'db.json');

function readDB() {
  if (!fs.existsSync(DB_PATH)) {
    const initial = { customers: [], drivers: [], trips: [] };
    fs.writeFileSync(DB_PATH, JSON.stringify(initial, null, 2));
    return initial;
  }
  const raw = fs.readFileSync(DB_PATH, 'utf-8');
  return JSON.parse(raw || '{"customers":[],"drivers":[],"trips":[]}');
}

function writeDB(data) {
  fs.writeFileSync(DB_PATH, JSON.stringify(data, null, 2));
}

function genId(prefix) {
  return `${prefix}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
}

module.exports = { readDB, writeDB, genId };
