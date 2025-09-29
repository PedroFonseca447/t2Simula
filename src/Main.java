import java.util.*;
import java.util.function.BiFunction;

public class Main {

    // ========= RNG base =========
    private static int previous = 1234;
    private static double nextRandom() {
        int a = 61, c = 37, M = 365_789;
        previous = ((a * previous) + c) % M;
        return (double) previous / M; // [0,1)
    }

    // ========= RNG orçamentado =========
    static class BudgetRNG {
        final double[] lista;
        int pos = 0;
        boolean exhausted = false;

        BudgetRNG(int count) {
            lista = new double[count];
            for (int i = 0; i < count; i++) lista[i] = nextRandom();
        }
        double next() {
            if (pos >= lista.length) {
                exhausted = true;
                return lista[lista.length - 1]; // valor dummy; loop vai parar
            }
            double u = lista[pos++];
            if (pos == lista.length) exhausted = true; // consumiu o 100.000º
            return u;
        }
    }

    // ========= Eventos =========
    enum EvType { ARRIVAL_EXT, DEPARTURE }

    static class Event implements Comparable<Event> {
        final double time;
        final EvType type;
        final int origin; // para ARRIVAL_EXT é -1; para DEPARTURE é a fila que terminou
        final int dest;   // para ARRIVAL_EXT é a fila destino; para DEPARTURE não usamos
        Event(double time, EvType type, int origin, int dest) {
            this.time = time; this.type = type; this.origin = origin; this.dest = dest;
        }
        public int compareTo(Event o) { return Double.compare(this.time, o.time); }
    }

    // ========= Rota =========
    static class Route {
        final int dest;      // id da fila destino; -1 = sai do sistema
        final double prob;   // probabilidade
        Route(int dest, double prob) { this.dest = dest; this.prob = prob; }
    }

    private static final double EPS = 1e-9;

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        // ====== Orçamento de aleatórios ======
        final int COUNT = 100_000;
        BudgetRNG rng = new BudgetRNG(COUNT);
        final BiFunction<Double, Double, Double> uniform =
                (a, b) -> a + (b - a) * rng.next();

        // ====== FILAS (exemplo/diagrama) ======
        // F1: G/G/1/3  (chegadas U(2,4), serviço U(1,2))  => cap total = 1 + 3 = 4
        Fila q1 = new Fila(1, 4, 2.0, 4.0, 1.0, 2.0);

        // F2: G/G/2/5  (sem chegadas externas, serviço U(4,6)) => cap total = 2 + 5 = 7
        // Para "sem chegadas externas", mantenha arrivalMin > arrivalMax (ex.: 10,0)
        Fila q2 = new Fila(2, 7, 10.0, 0.0, 4.0, 6.0);

        // F3: G/G/2/10 (sem chegadas externas, serviço U(5,15)) => cap total = 2 + 10 = 12
        Fila q3 = new Fila(2, 12, 10.0, 0.0, 5.0, 15.0);
        List<Fila> filas = List.of(q1, q2, q3);

        // Inicializa: F1 com 1ª chegada FIXA em t = 2.0; demais sem chegada externa
        q1.initWithFirstArrival(0.0, uniform, 2.0);
        q2.init(0.0, uniform);
        q3.init(0.0, uniform);

        // ====== ROTEAMENTO ======
        // De F1: 0.2 -> F2, 0.3 -> F3, 0.5 -> SAÍDA
        List<List<Route>> routes = new ArrayList<>();
        // De F1: 0.2 -> F2, 0.3 -> F3, 0.5 -> SAÍDA
        routes.add(Arrays.asList(new Route(1, 0.2), new Route(2, 0.3), new Route(-1, 0.5)));
        // F2 -> saída
        routes.add(Collections.singletonList(new Route(-1, 1.0)));
        // F3 -> saída
        routes.add(Collections.singletonList(new Route(-1, 1.0)));
        // ====== ESCALONADOR ======
        PriorityQueue<Event> sched = new PriorityQueue<>();
        if (!Double.isInfinite(q1.nextArrivalTime()))
            sched.add(new Event(q1.nextArrivalTime(), EvType.ARRIVAL_EXT, -1, 0));

        final double T_MAX_MIN  = Double.POSITIVE_INFINITY; // sem limite de tempo extra
        final int    MAX_EVENTS = Integer.MAX_VALUE;         // sem limite de eventos extra
        int events = 0;
        double t = 0.0; // relógio global

        // ====== LOOP GLOBAL ======
        while (!sched.isEmpty() && !rng.exhausted) {
            Event ev = sched.poll();
            double tNext = ev.time;
            if (tNext > T_MAX_MIN || events >= MAX_EVENTS) break;

            // avança TODAS as filas até tNext
            for (Fila f : filas) f.advanceTo(tNext);
            t = tNext;

            if (ev.type == EvType.ARRIVAL_EXT) {
                if (rng.exhausted) break; // não sorteia nada novo
                Fila q = filas.get(ev.dest);
                q.in(); // agenda próxima chegada externa e possivelmente um serviço

                // reagendar a próxima chegada externa desta fila
                if (!Double.isInfinite(q.nextArrivalTime()) && !rng.exhausted) {
                    sched.add(new Event(q.nextArrivalTime(), EvType.ARRIVAL_EXT, -1, ev.dest));
                }
                // garantir que o PRÓXIMO término desta fila esteja no escalonador
                double dep = q.nextDepartureTime();
                if (dep < Double.POSITIVE_INFINITY - EPS) {
                    sched.add(new Event(dep, EvType.DEPARTURE, ev.dest, -1));
                }

            } else { // DEPARTURE em ev.origin
                Fila from = filas.get(ev.origin);

                // valida se o evento corresponde ao topo atual de departures dessa fila
                double depTop = from.nextDepartureTime();
                if (depTop == Double.POSITIVE_INFINITY || Math.abs(depTop - tNext) > EPS) {
                    // evento antigo/duplicado -> ignora
                    continue;
                }

                // término de serviço confirmado
                from.out(); // pode agendar novo serviço (consome RNG)

                // roteamento a partir do 'origin' (só se ainda há orçamento)
                if (!rng.exhausted) {
                    int dest = sampleRoute(routes.get(ev.origin), uniform);
                    if (dest >= 0) {
                        Fila to = filas.get(dest);
                        to.inFromRouting(); // pode iniciar serviço (consome RNG)
                        double depTo = to.nextDepartureTime();
                        if (depTo < Double.POSITIVE_INFINITY - EPS) {
                            sched.add(new Event(depTo, EvType.DEPARTURE, dest, -1));
                        }
                    }
                }

                // reagenda o próximo término do origin (se houver)
                double depFrom = from.nextDepartureTime();
                if (depFrom < Double.POSITIVE_INFINITY - EPS) {
                    sched.add(new Event(depFrom, EvType.DEPARTURE, ev.origin, -1));
                }
            }

            events++;
        }

        // ====== SINCRONIZAÇÃO FINAL: garante acumular até t em TODAS as filas ======
        for (Fila f : filas) {
            if (f.now() < t) f.advanceTo(t);
        }

        // ====== RELATÓRIO ======
        System.out.println("==== RESULTADOS DA REDE ====");
        for (int i = 0; i < filas.size(); i++) {
            Fila q = filas.get(i);
            report("Fila " + (i + 1), q, q.getServidores(), q.getCapacidade());
        }
        System.out.printf("Tempo global da simulação: %.2f s%n", t * 60.0);
        System.out.printf("Aleatórios consumidos: %d de %d%n", rng.pos, COUNT);
    }

    // Escolhe destino com probabilidades cumulativas (usa uniform orçamentado)
    private static int sampleRoute(List<Route> routes, BiFunction<Double, Double, Double> uniform) {
        double u = uniform.apply(0.0, 1.0);
        double acc = 0.0;
        for (Route r : routes) {
            acc += r.prob;
            if (u < acc) return r.dest;
        }
        return routes.get(routes.size() - 1).dest; // fallback
    }

    private static void report(String name, Fila q, int servidores, int capacidade) {
        double totalMin = q.now();
        double[] timeInState = q.getTimesByState();

        System.out.println("*** " + name + " (G/G/" + servidores + "/" + Math.max(0, capacidade - servidores) + ") ***");
        System.out.printf("Servidores: %d | Cap.sistema: %d | Tam.fila: %d%n",
                servidores, capacidade, Math.max(0, capacidade - servidores));
        System.out.println("-----------------------------------------------");
        System.out.printf("%6s %18s %23s%n", "Estado", "Tempo (sec)", "Probabilidade");

        double probSum = 0.0;
        for (int s = 0; s <= capacidade; s++) {
            double timeSec = timeInState[s] * 60.0;
            double prob = (totalMin > 0) ? (timeInState[s] / totalMin) * 100.0 : 0.0;
            probSum += prob;
            System.out.printf("%6d %18.4f %22.4f%%%n", s, timeSec, prob);
        }

        System.out.println("Perdas: " + q.getLoss());
        System.out.printf("Tempo total (h): %.4f | Soma das probabilidades: %.4f%%%n",
                totalMin / 60.0, probSum);
        System.out.println();
    }
}
