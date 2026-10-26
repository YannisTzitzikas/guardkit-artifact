import json, platform, signal, statistics, sys, time
sys.path.insert(0, ".")

class Timeout(Exception):
    pass

def _alarm(signum, frame):
    raise Timeout()

signal.signal(signal.SIGALRM, _alarm)

def timed(fn, *args, limit=10.0):
    """Wall-clock time of fn(*args) in seconds, or inf if it exceeds `limit`."""
    signal.setitimer(signal.ITIMER_REAL, limit)
    t0 = time.perf_counter()
    try:
        r = fn(*args)
        return time.perf_counter() - t0, r
    except Timeout:
        return float("inf"), None
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)

def summary(xs):
    fin = [x for x in xs if x != float("inf")]
    return {"median": statistics.median(xs) if xs else None,
            "max": max(xs) if xs else None,
            "timeouts": len(xs) - len(fin), "n": len(xs)}

def env():
    import os
    cpu = "unknown"
    try:
        for line in open("/proc/cpuinfo"):
            if line.startswith("model name"):
                cpu = line.split(":", 1)[1].strip(); break
    except OSError:
        pass
    return {"python": platform.python_version(), "implementation": platform.python_implementation(),
            "machine": platform.machine(), "cpu": cpu, "cores": os.cpu_count(), "system": platform.system()}

def save(name, obj):
    obj = dict(obj); obj["environment"] = env()
    json.dump(obj, open(f"results/{name}.json", "w"), indent=1)
