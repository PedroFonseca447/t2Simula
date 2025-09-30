import java.util.PriorityQueue;
import java.util.function.BiFunction;

public class Fila {
    // Parâmetros fixos
    private final int servidores;           // nº de servidores
    private final int capacidadeTotal;      // capacidade TOTAL (em serviço + fila) = servidores + capFila
    private final double arrivalMin;        // janela chegada externa (min)
    private final double arrivalMax;        // janela chegada externa (max)
    private final double minService;        // serviço (min)
    private final double maxService;        // serviço (max)

    // Estado
    private double clock = 0.0;                     // relógio local da fila (min)
    private int customers = 0;                      // clientes no sistema
    private double nextArrival = Double.POSITIVE_INFINITY;  // +∞ => sem chegada externa
    private final PriorityQueue<Double> departures = new PriorityQueue<>(); // tempos absolutos de término

    // Estatística
    private final double[] timeInState;     // acumula tempo por estado 0..capacidadeTotal
    private int loss = 0;

    // RNG injetado (usa seu BudgetRNG indiretamente)
    private BiFunction<Double, Double, Double> uniform;

    public Fila(int servidores, int capacidadeTotal,
                double arrivalMin, double arrivalMax,
                double minService, double maxService) {

        if (servidores <= 0) throw new IllegalArgumentException("servidores deve ser >= 1");
        if (capacidadeTotal < servidores) throw new IllegalArgumentException("capacidadeTotal deve ser >= servidores");

        this.servidores = servidores;
        this.capacidadeTotal = capacidadeTotal;
        this.arrivalMin = arrivalMin;
        this.arrivalMax = arrivalMax;
        this.minService = minService;
        this.maxService = maxService;

        this.timeInState = new double[capacidadeTotal + 1];
    }

    // ========== Inicialização ==========
    public void init(double t0, BiFunction<Double, Double, Double> uniform) {
        this.uniform = uniform;
        this.clock = t0;
        this.customers = 0;
        this.departures.clear();
        // zera estatística
        for (int i = 0; i < timeInState.length; i++) timeInState[i] = 0.0;
        this.loss = 0;

        if (arrivalMin <= arrivalMax) {
            // há chegadas externas
            this.nextArrival = t0 + uniform.apply(arrivalMin, arrivalMax);
        } else {
            // sem chegadas externas
            this.nextArrival = Double.POSITIVE_INFINITY;
        }
    }

    /** Igual ao init, mas força a 1ª chegada externa em instante fixo. */
    public void initWithFirstArrival(double t0, BiFunction<Double, Double, Double> uniform, double firstArrivalAt) {
        init(t0, uniform);
        this.nextArrival = firstArrivalAt;
    }

    // ========== Avanço de tempo (não processa eventos) ==========
    public void advanceTo(double t) {
        if (t <= clock) return;
        double dt = t - clock;

        int state = Math.min(customers, capacidadeTotal);
        timeInState[state] += dt;

        clock = t;
    }

    // ========== Eventos ==========
    /** Chegada externa (se houver janela). Reagenda próxima chegada externa e agenda serviço se servidor livre. */
    public boolean in() {
        // reagenda próxima externa (se existir)
        if (arrivalMin <= arrivalMax) {
            nextArrival = clock + uniform.apply(arrivalMin, arrivalMax);
        } else {
            nextArrival = Double.POSITIVE_INFINITY;
        }

        if (customers >= capacidadeTotal) {
            loss++;
            return false;
        }
        customers++;

        // se há servidor ocioso, agenda um término para este cliente
        if (customers <= servidores) {
            double svc = uniform.apply(minService, maxService);
            departures.add(clock + svc);
        }
        return true;
    }

    /** Chegada por roteamento interno. Agenda serviço se servidor livre. */
    public boolean inFromRouting() {
        if (customers >= capacidadeTotal) {
            loss++;
            return false;
        }
        customers++;

        // se há servidor ocioso, agenda um término para este cliente
        if (customers <= servidores) {
            double svc = uniform.apply(minService, maxService);
            departures.add(clock + svc);
        }
        return true;
    }

    /** Término de serviço confirmado (consome o topo atual). Agenda novo término se ainda restarem >= servidores clientes. */
    public void out() {
        if (departures.isEmpty()) return; // proteção

        // assumimos que o clock global já foi avançado para o tempo do topo
        departures.poll();

        if (customers > 0) customers--; // um cliente saiu desta fila

        // Se ainda há pelo menos 'servidores' clientes, precisamos manter todos servidores ocupados.
        // Já existe (servidores-1) términos restantes na fila de 'departures' (depois do poll),
        // então adicionamos mais 1 término para o servidor que ficou livre agora.
        if (customers >= servidores) {
            double svc = uniform.apply(minService, maxService);
            departures.add(clock + svc);
        }
    }

    // ========== Consultas ==========
    public double nextArrivalTime()   { return nextArrival; }
    public double nextDepartureTime() { return departures.isEmpty() ? Double.POSITIVE_INFINITY : departures.peek(); }
    public double now()               { return clock; }
    public int getLoss()              { return loss; }
    public double[] getTimesByState() { return timeInState; }
    public int getServidores()        { return servidores; }
    public int getCapacidade()        { return capacidadeTotal; }
}
