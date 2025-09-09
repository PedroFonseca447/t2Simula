import java.util.PriorityQueue;
import java.util.function.BiFunction;

public class Fila {

    // --- Propriedades (mantendo os nomes que você escolheu) ---
    private double arrivalMin;
    private double arrivalMax;
    private int Servidores;
    private int Loss;
    private int Capacidade;
    private int Customers;
    private final double minService;    // U(minService, maxService)
    private final double maxService;

    // --- Estado da simulação ---
    private double clock = 0.0;                 // relógio da fila
    private double nextArrival = Double.NaN;    // instante da próxima chegada
    private final PriorityQueue<Double> departures = new PriorityQueue<>();

    // --- Estatística ---
    private final double[] times; // tempo acumulado por estado 0..Capacidade

    // --- Fonte de aleatórios U(a,b) (vem de fora: Main) ---
    private BiFunction<Double, Double, Double> uniform;

    // ---------- Construtor ----------
    public Fila(int Servidores, int Capacidade,
                double arrivalMin, double arrivalMax,
                double minService, double maxService) {
        this.Servidores = Servidores;
        this.Capacidade = Capacidade;
        this.arrivalMin = arrivalMin;
        this.arrivalMax = arrivalMax;
        this.minService = minService;
        this.maxService = maxService;
        this.times = new double[Capacidade + 1];
    }

    // ---------- Inicialização ----------
    public void init(double t0, BiFunction<Double, Double, Double> uniform) {
        this.clock = t0;
        this.uniform = uniform;
        this.nextArrival = t0 + uniform.apply(arrivalMin, arrivalMax);
    }

    // ---------- Procedimentos ----------
    public void in() {
        // agenda a próxima chegada
        nextArrival = clock + uniform.apply(arrivalMin, arrivalMax);

        if (Customers < Capacidade) {
            int busyBefore = Math.min(Customers, Servidores);
            Customers++;

            // se abriu atendimento imediatamente, agenda término
            if (Math.min(Customers, Servidores) > busyBefore) {
                departures.add(clock + uniform.apply(minService, maxService));
            }
        } else {
            // cheio -> perda
            Loss++;
        }
    }

    public void out() {
        departures.poll(); // remove o término que ocorreu agora
        Customers--;

        // se ainda há gente esperando (fila), inicia novo serviço
        if (Customers >= Servidores) {
            departures.add(clock + uniform.apply(minService, maxService));
        }
    }

    // ---------- Direção do tempo / agendamento ----------
    public double nextArrivalTime() { return nextArrival; }

    public double nextDepartureTime() {
        return departures.isEmpty() ? Double.POSITIVE_INFINITY : departures.peek();
    }

    public void advanceTo(double tNext) {
        times[Customers] += (tNext - clock);
        clock = tNext;
    }

    public double now() { return clock; }

    public double[] getTimesByState() { return times; }

    // ---------- Getters e Setters ----------
    public int getCustomers() { return Customers; }
    public void setCustomers(int Customers) { this.Customers = Customers; }

    public int getLoss() { return Loss; }
    public void setLoss(int Loss) { this.Loss = Loss; }

    public int getCapacidade() { return Capacidade; }
    public void setCapacidade(int Capacidade) { this.Capacidade = Capacidade; }

    public int getServidores() { return Servidores; }
    public void setServidores(int Servidores) { this.Servidores = Servidores; }

    public double getArrivalMin() { return arrivalMin; }
    public void setArrivalMin(double arrivalMin) { this.arrivalMin = arrivalMin; }

    public double getArrivalMax() { return arrivalMax; }
    public void setArrivalMax(double arrivalMax) { this.arrivalMax = arrivalMax; }
}
