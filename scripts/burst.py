#!/usr/bin/env python3
import concurrent.futures, json, sys, time, urllib.request, urllib.error

BASE=sys.argv[1].rstrip("/")
N=int(sys.argv[2]) if len(sys.argv)>2 else 2000
ADMIN="admin-local"

def request(method, path, body=None, token=None, idem=None):
    data=json.dumps(body).encode() if body is not None else None
    headers={"Content-Type":"application/json"}
    if token: headers["Authorization"]="Bearer "+token
    if idem: headers["X-Idempotency-Key"]=idem
    req=urllib.request.Request(BASE+path,data=data,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=30) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try: payload=json.loads(e.read().decode() or "{}")
        except Exception: payload={}
        return e.code,payload
    except Exception as e:
        return 599,{"error":str(e)}

# Create a fresh show. The API accepts idempotency_key in body.
status,show=request("POST","/shows",{"name":"burst-"+str(int(time.time())),"seats":["A1","A2","A3","A4","A5","A6","A7","A8"],"price_paise":25000,"per_user_limit":4},ADMIN)
if status not in (200,201):
    raise SystemExit(f"create show failed: {status} {show}")
sid=show["id"]
print("show",sid,"hot seat A1","requests",N)

def hot(i):
    return request("POST",f"/shows/{sid}/reserve",{"seats":["A1"],"idempotency_key":f"hot-{i}"},f"user-{i}")

with concurrent.futures.ThreadPoolExecutor(max_workers=min(256,N)) as ex:
    results=list(ex.map(hot,range(N)))

from collections import Counter
codes=Counter(r[0] for r in results)
reasons=Counter(r[1].get("code","unknown") for r in results if r[0] == 409)
print("hot-seat HTTP outcomes:",dict(codes))
print("decline reasons:",dict(reasons))

# Same-key replay: first request succeeds, ten retries must all return the same reservation.
key="replay-key"
def replay(i):
    return request("POST",f"/shows/{sid}/reserve",{"seats":["A2"],"idempotency_key":key},"replay-user",key)
with concurrent.futures.ThreadPoolExecutor(max_workers=32) as ex:
    replay_results=list(ex.map(replay,range(32)))
print("same-key outcomes:",Counter(r[0] for r in replay_results))

# Per-user concurrency: 10 distinct seats, same token, limit=4 => exactly four confirmations.
def limit(i):
    return request("POST",f"/shows/{sid}/reserve",{"seats":[f"A{i+3}"],"idempotency_key":f"limit-{i}"},"limited-user")
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
    limit_results=list(ex.map(limit,range(5)))
print("per-user outcomes:",Counter(r[0] for r in limit_results),
      "reasons:",Counter(r[1].get("code","unknown") for r in limit_results if r[0]==409))

status,state=request("GET",f"/shows/{sid}")
if status != 200: raise SystemExit(f"state failed: {status} {state}")
counts=(state["available_seats"],state["held_seats"],state["confirmed_seats"],state["total_seats"])
print("final reconciliation:",counts,"sum=",sum(counts[:3]))
if sum(counts[:3]) != counts[3]:
    raise SystemExit("RECONCILIATION FAILED")
if codes.get(500,0) or codes.get(599,0):
    raise SystemExit("5xx/network errors observed in hot-seat burst")
