# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java implementation of a Claude Code-style Agent learning project. The project uses a progressive learning approach with stages S01-S12 (and SFull), each incrementally introducing new capabilities. The core architecture separates runtime execution from capability configuration, allowing the same AgentRuntime to support different feature levels through StageConfig.

## Common Commands

### Backend (Java/Maven)

**Build the project:**
```bash
mvn compile
```

**Run specific stages:**
```bash
# Minimal agent loop
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S01AgentLoop

# Tool use capabilities
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S02ToolUse

# Todo management
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S03TodoWrite

# Subagent spawning
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S04Subagent

# Skill loading
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S05SkillLoading

# Context compression
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S06ContextCompact

# Task system
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S07TaskSystem

# Background tasks
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S08BackgroundTasks

# Agent teams
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S09AgentTeams

# Team protocols
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S10TeamProtocols

# Autonomous agents
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S11AutonomousAgents

# Worktree task isolation
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S12WorktreeTaskIsolation

# Full capabilities
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.SFull
```

### Frontend (Next.js)

```bash
cd web
npm install
npm run dev          # Start development server at http://localhost:3000
npm run build        # Build for production
npm start            # Start production server
```

## Architecture Overview

The project follows a layered architecture with clear separation of concerns:

**Core Components:**
- **Launcher**: Unified entry point - delegates stage configuration to the unified runtime
- **AppContext**: Application assembler - creates and wires all shared services in dependency order
- **AgentRuntime**: The central execution engine - implements the main agent loop (user input → model call → tool execution → result feedback)
- **StageConfig**: Capability configuration - defines which tools, features, and system prompts are available per stage
- **AnthropicClient**: LLM API client - handles Anthropic-compatible API calls

**Advanced Feature Managers:**
- **TaskManager**: File-based task board for task planning, claiming, and dependencies
- **WorktreeManager**: Task isolation through worktree lane management
- **BackgroundManager**: Async execution of long-running commands with result injection
- **TeammateManager**: Multi-agent collaboration, spawning teammates, inbox communication, autonomous task claiming
- **CompressionService**: Context window management through message history compression
- **SkillLoader**: External knowledge loading from `skills/` directory

**Tool Layer:**
- **CommandTools**: File operations (read/write/edit) and shell command execution with basic safety checks
- **TodoManager**: In-memory todo list maintenance

**Utilities:**
- **EnvConfig**: Environment variable loading (.env + system env, system env takes precedence)
- **WorkspacePaths**: Working directory and state directory path management (.tasks, .team, .worktrees, transcripts)
- **JsonUtils**: JSON serialization/deserialization utilities

## Key Design Principles

**1. Runtime vs Capability Separation**
- `AgentRuntime` handles "how to run" (the loop, tool dispatch, message management)
- `StageConfig` handles "what can be done" (available tools, feature flags, system prompts)
- Same runtime supports all stages through configuration changes

**2. Tool-Based Execution**
- Model makes decisions, local Java code executes actions
- Tools are the agent's "arms and legs" - bash, read_file, write_file, edit_file
- Advanced features (tasks, teams, compression) are also exposed as tools

**3. Message History as Working Memory**
- All agent state flows through the message history: user inputs, tool results, teammate messages, background task results
- Understanding message flow is key to understanding agent continuity

**4. Externalized State for Complex Tasks**
- Long tasks require explicit state management beyond conversation memory
- Todo, Task, Worktree, and Team capabilities all solve state externalization

**5. Multi-Agent Collaboration via Simple Protocols**
- File inbox (JSONL) for low-dependency communication
- JSON task board for shared state
- Simple protocols for shutdown and approval
- No complex message queue infrastructure

## Stage Progression

The learning stages introduce concepts incrementally:

- **S01**: Minimal agent loop (understand step-by-step execution)
- **S02**: File tools (why coding agents need file I/O)
- **S03**: Todo list (state tracking for long tasks)
- **S04**: Subagents (fresh context for subproblems)
- **S05**: Skills (external knowledge loading)
- **S06**: Context compression (window management)
- **S07**: Task system (persistent task planning)
- **S08**: Background tasks (non-blocking long operations)
- **S09**: Agent teams (lead/teammate communication)
- **S10**: Team protocols (shutdown/approval coordination)
- **S11**: Autonomous teammates (automatic task claiming)
- **S12**: Worktree isolation (directory-level task separation)
- **SFull**: All capabilities combined

## Environment Configuration

Required environment variables in `.env`:
- `MODEL_ID`: Model identifier to use
- `ANTHROPIC_API_KEY`: API key for Anthropic-compatible service
- `ANTHROPIC_BASE_URL`: Base URL (optional, defaults to https://api.anthropic.com)

System environment variables override `.env` values.

## Working Directory Structure

The project creates and manages several state directories:
- `.tasks/`: Task board files
- `.team/`: Teammate inboxes and state
- `.worktrees/`: Isolated worktree lanes
- `transcripts/`: Conversation transcripts

Paths are managed by `WorkspacePaths` to prevent directory traversal attacks.

## Technology Stack

**Backend:**
- Java 17
- Maven for build management
- Jackson for JSON serialization
- dotenv-java for environment configuration

**Frontend:**
- Next.js 16
- React 19
- TypeScript
- TailwindCSS
- Framer Motion
- Unified/remark/rehype for markdown processing

**Dependencies:**
- Minimal external dependencies - core agent logic uses only Jackson and dotenv-java
- HTTP client uses Java's built-in `java.net.http`
