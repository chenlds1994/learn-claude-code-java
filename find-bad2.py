import re

c = open('learn-claude-code-tutorial.drawio', 'r', encoding='utf-8').read()
lines = c.split('\n')
found = []

for i, line in enumerate(lines):
    if 'value=' not in line:
        continue
    start = 0
    while True:
        vs = line.find('value="', start)
        if vs == -1:
            break
        ve = -1
        j = vs + 7
        while j < len(line):
            if line[j] == '"':
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

with open('bad-lines.txt', 'w', encoding='utf-8') as f:
    f.write('Found ' + str(len(found)) + ' bad lines\n')
    for item in found:
        f.write('Line ' + str(item[0]) + ': ' + item[1][:150] + '\n')
