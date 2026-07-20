import re

c = open('learn-claude-code-tutorial.drawio', 'r', encoding='utf-8').read()
lines = c.split('\n')
found = []

for i, line in enumerate(lines):
    if 'value=' not in line:
        continue
    # Find all value="..." segments, handling nested quotes
    start = 0
    while True:
        vs = line.find('value="', start)
        if vs == -1:
            break
        # Find the real end of value attribute
        # Need to handle: value="abc" style=... (normal)
        # vs value="abc"def" style=... (broken - nested quote)
        ve = -1
        j = vs + 7
        while j < len(line):
            if line[j] == '"':
                # Check if preceded by &quot; or &#34;
                prev = line[max(0, j-5):j+1]
                prev2 = line[max(0, j-4):j+1]
                if prev == '&quot;' or prev2 == '&#34;':
                    j += 1
                    continue
                ve = j
                break
            j += 1
        if ve == -1:
            start = vs + 1
            continue
        val = line[vs+7:ve]
        # Check if there are unescaped quotes inside the value
        has_unescaped = False
        for k in range(len(val)):
            if val[k] == '"':
                if k >= 5 and val[k-5:k+1] == '&quot;':
                    continue
                if k >= 4 and val[k-4:k+1] == '&#34;':
                    continue
                has_unescaped = True
                break
        if has_unescaped:
            found.append((i+1, line.strip()))
        start = ve + 1

print('Found', len(found), 'bad lines')
for f in found:
    print('Line', f[0], ':', f[1][:150])
