import time
import hashlib
from commands import getoutput
from commands import getstatusoutput
import random
import threading

f=open('ab', 'w')
llen="242"
username="user-89npjpup"
timestamp=str(int(time.time()))

parent=""
tree=""
sha1_body=""

threadLock = threading.Lock()

running = True

class myThread (threading.Thread):
    def __init__(self, threadID):
        threading.Thread.__init__(self)
        self.threadID = threadID

    def run(self):
        print "Starting " + str(self.threadID)
        xx = solve(self.threadID*100000000)
        if(xx):
            running = False
            threadLock.acquire()
            print "Pushing date from thread "+str(self.threadID)
            push_data(xx[1], xx[2])
            threadLock.release()

def push_data(commit_msg, sha1):
    f.write(commit_msg)
    print sha1
    getoutput("git hash-object -t commit -w ab")
    getoutput("git checkout master")
    getoutput("git merge "+sha1)
    print "do git push origin master, NOW!!!!"
    print getstatusoutput("git push origin master")
    

def solve(counter):
    t0 = time.clock()
    while(running and (time.clock() - t0 < 10)):
        commit_msg=sha1_body+("%08d" % counter)
        sha1_str="commit "+llen+"\0"+commit_msg
        sha1=hashlib.sha1(sha1_str).hexdigest()
        if(sha1 < difficulty):
            print "found sha1"
            return (True, commit_msg, sha1)
        counter=counter+1

    return False

if __name__ == "__main__":

    getoutput("git reset --hard HEAD")
    getoutput("git fetch")
 
    while(True):
        threads = []
        print "will fetch and restart"
        difficulty=open('difficulty.txt').readline().strip()
        getoutput("git reset --hard origin/HEAD")
        getoutput("git pull")

        ## Modify ledger file.
        fp=open('LEDGER.txt', 'r')
        lines=fp.readlines()
        fp.close()

        fp=open('LEDGER.txt', 'w')
        found=False
        for i in range(len(lines)):
            line = lines[i]
            if username in line.split(":"):
                lines[i] = line[1] + ":" + str(int(line[2])+1)
                found=True
        
        fp.writelines(lines)

        if(not found):
            fp.write(username+': 1\n')
        fp.close()
        ## Modify ledger done.

        getoutput('git add LEDGER.txt')

        parent=getoutput("git rev-parse HEAD")
        tree=getoutput("git write-tree")
        sha1_body="tree "+tree+"\nparent "+parent+"\nauthor Aamir Khan <syst3m.w0rm@gmail.com> "+timestamp+" +0000\ncommitter Aamir Khan <syst3m.w0rm@gmail.com> "+timestamp+" +0000\n\nGive me a Gitcoin "

        num_of_threads = 25

        # Create new threads
        for x in range(num_of_threads):
            if(not  running):
                break

            t = myThread(x)
            t.start()
            threads.append(t)

        # Wait for all threads to complete
        for t in threads:
            t.join(60)
