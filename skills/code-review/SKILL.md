---
name: code-review
description: Perform thorough code reviews with security, performance, and maintainability analysis. Use when user asks to review code, check for bugs, or audit a codebase.
---

# Code Review Skill

You now have expertise in conducting comprehensive code reviews. Follow this structured approach:

## Review Checklist

### 1. Security (Critical)

Check for:
- [ ] **Injection vulnerabilities**: SQL, command, XSS, template injection
- [ ] **Authentication issues**: Hardcoded credentials, weak auth
- [ ] **Authorization flaws**: Missing access controls, IDOR
- [ ] **Data exposure**: Sensitive data in logs, error messages
- [ ] **Cryptography**: Weak algorithms, improper key management
- [ ] **Dependencies**: Known vulnerabilities (check with `npm audit`, `pip-audit`)

### 2. Correctness

Check for:
- [ ] **Logic errors**: Off-by-one, null handling, edge cases
- [ ] **Race conditions**: Concurrent access without synchronization
- [ ] **Resource leaks**: Unclosed files, connections, memory
- [ ] **Error handling**: Swallowed exceptions, missing error paths
- [ ] **Type safety**: Implicit conversions, any types

### 3. Performance

Check for:
- [ ] **N+1 queries**: Database calls in loops
- [ ] **Memory issues**: Large allocations, retained references
- [ ] **Blocking operations**: Sync I/O in async code
- [ ] **Inefficient algorithms**: O(n^2) when O(n) possible
- [ ] **Missing caching**: Repeated expensive computations

### 4. Maintainability

Check for:
- [ ] **Naming**: Clear, consistent, descriptive
- [ ] **Complexity**: Functions > 50 lines, deep nesting > 3 levels
- [ ] **Duplication**: Copy-pasted code blocks
- [ ] **Dead code**: Unused imports, unreachable branches
- [ ] **Comments**: Outdated, redundant, or missing where needed

### 5. Testing

Check for:
- [ ] **Coverage**: Critical paths tested
- [ ] **Edge cases**: Null, empty, boundary values
- [ ] **Mocking**: External dependencies isolated
- [ ] **Assertions**: Meaningful, specific checks

## Review Output Format

```markdown
## Code Review: [file/component name]

### Summary
[1-2 sentence overview]

### Critical Issues
1. **[Issue]** (line X): [Description]
   - Impact: [What could go wrong]
   - Fix: [Suggested solution]

### Improvements
1. **[Suggestion]** (line X): [Description]

### Positive Notes
- [What was done well]

### Verdict
[ ] Ready to merge
[ ] Needs minor changes
[ ] Needs major revision
```

## Common Patterns to Flag

- SQL / command injection
- Mutable shared state
- Resource leak
- Missing null / boundary handling
- Callback hell or deeply nested logic

## Review Workflow

1. **Understand context**: Read description and linked issues
2. **Run the code**: Build and test locally if possible
3. **Read top-down**: Start with main entry points
4. **Check tests**: Are changes tested?
5. **Security scan**: Run automated tools
6. **Manual review**: Use checklist above
7. **Write feedback**: Be specific and kind
