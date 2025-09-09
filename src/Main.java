import java.util.Locale;
import java.util.function.BiFunction;

public class Main {

    // ---------- RNG ----------
    private static int previous = 1234;
    private static double nextRandom() {
        int a = 61, c = 37, M = 365789;
        previous = ((a * previous) + c) % M;
        return (double) previous / M; // [0,1)
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        // ---------- U(a,b) on-demand ----------
        BiFunction<Double, Double, Double> uniform = (a, b) -> {
            double u = nextRandom();
            return a + (b - a) * u;
        };

        // ---------- Parâmetros ----------
        final double ARR_MIN_Q1 = 2.0, ARR_MAX_Q1 = 5.0;
        final double SVC_MIN_Q1 = 3.0, SVC_MAX_Q1 = 5.0;
        final int SERV_Q1 = 1, CAP_Q1 = 6;

        final double ARR_MIN_Q2 = 1e99, ARR_MAX_Q2 = 1e99; // sem chegadas externas
        final double SVC_MIN_Q2 = 2.0, SVC_MAX_Q2 = 4.0;
        final int SERV_Q2 = 1, CAP_Q2 = 5;

        // ---------- Duas filas (tua classe) ----------
        Fila q1 = new Fila(SERV_Q1, CAP_Q1, ARR_MIN_Q1, ARR_MAX_Q1, SVC_MIN_Q1, SVC_MAX_Q1);
        Fila q2 = new Fila(SERV_Q2, CAP_Q2, ARR_MIN_Q2, ARR_MAX_Q2, SVC_MIN_Q2, SVC_MAX_Q2);
        q1.init(0.0, uniform);
        q2.init(0.0, uniform);

        final double EPS = 1e-9;

        // ---------- Critérios de parada saudáveis ----------
        final double T_MAX_MIN   = 10_000.0;   // tempo simulado máximo (minutos)
        final int    MAX_EVENTS  = 2_000_000;  // segurança
        int events = 0;

        // ---------- Loop principal ----------
        while (true) {
            double tArrQ1 = q1.nextArrivalTime();
            double tDepQ1 = q1.nextDepartureTime();
            double tDepQ2 = q2.nextDepartureTime();

            double tNext = Math.min(tArrQ1, Math.min(tDepQ1, tDepQ2));
            if (Double.isInfinite(tNext)) break;               // nada mais a acontecer
            if (tNext > T_MAX_MIN || events >= MAX_EVENTS) {   // critérios de parada
                // avança as filas até T_MAX_MIN para fechar estatística bonitinha
                double cutoff = Math.min(tNext, T_MAX_MIN);
                q1.advanceTo(cutoff);
                q2.advanceTo(cutoff);
                break;
            }

            // acumula tempo em ambas as filas
            q1.advanceTo(tNext);
            q2.advanceTo(tNext);

            // resolve evento
            if (Math.abs(tNext - tArrQ1) < EPS) {
                q1.in();              // chegada externa em Q1
            } else if (Math.abs(tNext - tDepQ1) < EPS) {
                q1.out();             // término em Q1
                q2.in();              // PASSAGEM: entra em Q2 no mesmo instante
            } else {
                q2.out();             // término em Q2
            }

            events++;
        }

        // ---------- Relatório ----------
        report("Q1", q1, SERV_Q1, CAP_Q1, ARR_MIN_Q1, ARR_MAX_Q1, SVC_MIN_Q1, SVC_MAX_Q1);
        System.out.println();
        report("Q2", q2, SERV_Q2, CAP_Q2, Double.NaN, Double.NaN, SVC_MIN_Q2, SVC_MAX_Q2);
    }

    private static void report(String name, Fila q, int servidores, int capacidade,
                               double arrMin, double arrMax, double svcMin, double svcMax) {
        double totalMin = q.now();
        double[] timeInState = q.getTimesByState();

        System.out.println("*** " + name + " (G/G/" + servidores + "/" + Math.max(0, capacidade - servidores) + ") ***");
        if (!Double.isNaN(arrMin))
            System.out.printf("Chegadas (min): %.1f ... %.1f%n", arrMin, arrMax);
        System.out.printf("Serviço   (min): %.1f ... %.1f%n", svcMin, svcMax);
        System.out.printf("Servidores: %d | Capacidade sistema: %d | Tamanho fila: %d%n",
                servidores, capacidade, Math.max(0, capacidade - servidores));
        System.out.println("------------------------------------------------------------");
        System.out.printf("%6s %18s %23s%n", "Estado", "Tempo (sec)", "Probabilidade");

        double probSum = 0.0;
        for (int s = 0; s <= capacidade; s++) {
            double timeSec = timeInState[s] * 60.0;
            double prob = (totalMin > 0) ? (timeInState[s] / totalMin) * 100.0 : 0.0;
            probSum += prob;
            System.out.printf("%6d %18.2f %22.2f%%%n", s, timeSec, prob);
        }

        System.out.println("Perdas: " + q.getLoss());
        System.out.printf("Tempo total de simulação (h): %.2f%n", (totalMin * 60.0) / 3600.0);
        System.out.printf("Soma das probabilidades: %.2f%%%n", probSum);
    }
}
