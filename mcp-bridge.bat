@echo off
REM WorldWind MCP stdio-to-HTTP bridge
REM seaglassfoundry.com
REM
REM Claude Code spawns this via stdio. It forwards JSON-RPC to the
REM WorldWind HTTP server running in Eclipse on port 8384.

setlocal
set PROJECT_DIR=%~dp0
java -cp "%PROJECT_DIR%target\classes" gov.nasa.worldwindx.mcp.McpStdioBridge %*
