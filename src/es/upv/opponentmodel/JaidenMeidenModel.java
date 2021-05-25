package es.upv.opponentmodel;

import java.util.*;
import java.util.stream.Collectors;

import genius.core.Bid;
import genius.core.boaframework.BOAparameter;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.Objective;
import genius.core.issue.Value;
import genius.core.utility.AdditiveUtilitySpace;
import negotiator.boaframework.opponentmodel.tools.UtilitySpaceAdapter;

public class JaidenMeidenModel extends OpponentModel {
	
	/*
	 * Define aquí los atributos de la clase y el objeto que necesites
	 */
	Bid lastOffert; //Almacena la anterior oferta
	Bid newOffert; //Almacena la ultima oferta
	int countOffers;
	double delta;
	double multa;
	List<Integer> issuesList;
	Map<Integer, Double> weights;
	Map<Integer, Map<Value, Integer>> frecuencies;

	@Override
	public String getName() {
		return "Jaiden Meiden (Mejora)";
	}
	
	@Override
	public void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);
		
		/*En esta parte del código debes poner cualquier instrucción que necesites
		 * para la inicialización del modelo de aprendizaje
		 */
		countOffers = 0;
		delta = parameters.get("delta");
		multa = parameters.get("multa");
		weights = new HashMap<>();
		frecuencies = new HashMap<>();
		issuesList = negotiationSession.getIssues().stream().map(Objective::getNumber).collect(Collectors.toList());
		int issuesSize = issuesList.size();
		for (int issueId : issuesList) {
			weights.put(issueId, 1.0 / issuesSize);
			frecuencies.put(issueId, null);
		}
	}
	
	@Override
	protected void updateModel(Bid bid, double time) {
		 /*
		  * En esta parte del código debes poner la lógica a realizar por el modelo
		  * cuando una oferta del oponente es recibida
		  */

		// Actualizar el número de ofertas recibidas,
		// así como la última oferta recibida del oponente.
		lastOffert = newOffert;
		newOffert = bid;
		countOffers++;

		//Obtenemos los valos de la oferta anterior
		List<String> lastValuesList = new ArrayList<>();
		for (Map.Entry<Integer, Value> entry : lastOffert.getValues().entrySet()) {
			String value = entry.getValue().toString();
			lastValuesList.add(value);
		}

		for (Map.Entry<Integer, Value> entry : newOffert.getValues().entrySet()) {
			int issueId = entry.getKey();
			Value bidValue = entry.getValue();
			// Actualizar los pesos wi
			double oldWeight = weights.get(issueId);
			// Buscamos si alguno de los valores de la nueva oferta está repetida y si esta repetido, se penaliza
			if (lastValuesList.contains(bidValue.toString())) {
				weights.put(issueId, oldWeight - multa); //Penalizanción.
			}else {
				// El peso para un atributo i únicamente debería actualizarse si el valor ofrecido
				// por el oponente en ese atributo NO ha cambiado con respecto a la última oferta
				// recibida.
				if (newOffert.getValue(issueId) == bidValue) {
					weights.put(issueId, oldWeight + delta);
				}
			}
			// Cuenta de cuántas veces ha aparecido cada
			// valor de atributo en las ofertas del oponente y
			// debera actualizarse siempre.
			Map<Value, Integer> valueCounter = frecuencies.get(issueId);
			Integer valueCount = null;
			if (valueCounter != null) {
				valueCount = valueCounter.get(bidValue);
			}
			if (valueCount != null) {
				valueCounter.put(bidValue, valueCount + 1);
			} else {
				valueCounter = new HashMap<>();
				valueCounter.put(bidValue, 1);
				frecuencies.put(issueId, valueCounter);
			}
		}


	}
	
	@Override
	public double getBidEvaluation(Bid bid) {
		/*
		 * En esta parte del modelo debes poner la lógica empleada para que, dada una oferta,
		 * se utilice el modelo de las preferencias del oponente para determinar su utilidad estimada
		 */
		double utility = 0;

		// Calculamos la suma del total de los pesos wi
		// Este paso es necesario ya que al ir sumando el valor delta,
		// los pesos pueden haber cambiado su escala de 0 a 1. Usaremos
		// esta suma de pesos para normalizar los pesos una vez lo necesitemos.
		double weightsSum = weights.values().stream().mapToDouble(Double::doubleValue).sum();

		// Escalar los pesos (si es necesario)
		if (weightsSum > 1) {
			for (Map.Entry<Integer, Double> entry : weights.entrySet()) {
				int issueId = entry.getKey();
				double weight = entry.getValue();
				weight /= weightsSum;
				weights.put(issueId, weight);
			}
		}

		// Para cada atributo de la oferta a evaluar, se obtiene el número de veces que ha aparecido
		// el valor de dicho atributo en las ofertas enviadas por el oponente. La estimación de Vi
		// para dicho atributo será calculada de acuerdo a la fórmula: ** V_i_t(X_i) = #X_i/r **.
		// Donde:
		// ** #X_i ** representa el número de veces que el valor Xi ha sido recibido del oponente en el histórico de la negociación
		// ** r ** representa el número total de ofertas recibidas del oponente

		// Obtener contadores de los valores, actualizar V_i_t y calcular la utilidad
		for (Map.Entry<Integer, Map<Value, Integer>> entry : frecuencies.entrySet()) {
			int issueId = entry.getKey();
			Map<Value, Integer> value = entry.getValue();

			if (value != null) {
				Integer valueCount = value.get(bid.getValue(issueId));
				if (valueCount != null) {
					double weight = weights.get(issueId);
					// V_i_t(X_i) = #X_i/r
					double function = (double) valueCount / countOffers;
					// Acumulamos la utilidad aportada por la oferta como la suma acumulada del
					// peso normalizado del atributo por la estimación de V_i_t.
					// Formula: U(X) = Sumatoria(W_i * V_i_t(X_i))
					utility += weight * function;
				}
			}
		}

		return utility;
	}
	
	@Override
	public AdditiveUtilitySpace getOpponentUtilitySpace() {
		return new UtilitySpaceAdapter(this, this.negotiationSession.getDomain());
	}
	
	@Override
	public Set<BOAparameter> getParameterSpec(){
		Set<BOAparameter> set = new HashSet<BOAparameter>();
		/* Aquí describe los parámetros que necesita el algoritmo de aprendizaje. Ejemplos:
			set.add(new BOAparameter("n", 20.0, "The number of own best offers to be used for genetic operations"));
			set.add(new BOAparameter("n_opponent", 20.0, "The number of opponent's best offers to be used for genetic operations"));
		*/

		set.add(new BOAparameter("delta", 0.001, "Weight increment"));
		set.add(new BOAparameter("multa", 0.1, "Weight decrement"));
		return set;
	}

}
