import time
import hashlib
from commands import getoutput
from commands import getstatusoutput

f=open('ab', 'w')
llen="242"
username="user-89npjpup"

def solve(t0):

    timestamp=str(int(time.time()))
    parent=getoutput("git rev-parse HEAD")
    tree=getoutput("git write-tree")
    sha1_body="tree "+tree+"\nparent "+parent+"\nauthor Aamir Khan <syst3m.w0rm@gmail.com> "+timestamp+" +0000\ncommitter Aamir Khan <syst3m.w0rm@gmail.com> "+timestamp+" +0000\n\nGive me a Gitcoin "

    counter=0
    while(time.clock() - t0 < 2*60):
        commit_msg=sha1_body+("%08d" % counter)
        sha1_str="commit "+llen+"\0"+commit_msg
        sha1=hashlib.sha1(sha1_str).hexdigest()
        if(sha1 < difficulty):
            f.write(commit_msg)
            print sha1
            getoutput("git hash-object -t commit -w ab")
            getoutput("git checkout master")
            getoutput("git merge "+sha1)
            print "do git push origin master, NOW!!!!"
            print getstatusoutput("git push origin master")
        counter=counter+1

if __name__ == "__main__":

    while(True):
        print "will fetch and restart"
        difficulty=open('difficulty.txt').readline().strip()
        getoutput("git reset --hard origin/HEAD")
        getoutput("git pull")

        ## Modify ledger file.
        fp=open('LEDGER.txt', 'a')
        lines=f.readlines()
        found=False
        for i in range(len(lines)):
            line = lines[i]
            if username in line.split(":")
                lines[i] = line[1] + ":" str(int(line[2])+1)
                found=True

        fp.writelines(lines)
        if(!found):
            fp.write(username+': 1\n')
        fp.close()
        ## Modify ledger done.

        getoutput('git add LEDGER.txt')

        t0=time.clock()
        solve(t0)
