"use strict";

const http = require("http");
const {createStandaloneGos} = require("./gos/standalone");

const gos = createStandaloneGos();
const originalCreateServer = http.createServer.bind(http);

http.createServer = function patchedCreateServer(legacyHandler) {
  return originalCreateServer(async (req, res) => {
    try {
      const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
      if (url.pathname.startsWith("/api/gos/")) {
        return gos.handle(req, res, url);
      }
      return legacyHandler(req, res);
    } catch (error) {
      console.error("gos_wrapper_error", error);
      if (!res.headersSent) {
        res.writeHead(500, {"Content-Type":"application/json; charset=utf-8"});
      }
      if (!res.writableEnded) res.end(JSON.stringify({ok:false,error:"gos_wrapper_error"}));
    }
  });
};

require("./server");
