package es.upv.opponentmodel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.boaframework.BOAparameter;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Objective;
import genius.core.issue.Value;
import genius.core.utility.AdditiveUtilitySpace;
import negotiator.boaframework.opponentmodel.tools.UtilitySpaceAdapter;

public class JaidenMeidenModel extends OpponentModel {
	
	/*
	 * Define aquí los atributos de la clase y el objeto que necesites
	 */
	Bid lastOffert;
	int countOffers;
	double delta;
	List<Integer> issueIds;
	Map<Integer, Double> weights;
	Map<Integer, Map<Value, Integer>> values;
	
	

	@Override
	public String getName() {
		return "Jaiden Meiden";
	}
	
	@Override
	public void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);
		
		/*En esta parte del código debes poner cualquier instrucción que necesites
		 * para la inicialización del modelo de aprendizaje
		 */
		countOffers = 0;
		delta = parameters.get("delta");
		weights = new HashMap<>();
		values = new HashMap<>();
		issueIds = negotiationSession.getIssues().stream().map(Objective::getNumber).collect(Collectors.toList());
		int issueNumbers = issueIds.size();
		for (int issueId : issueIds) {
			weights.put(issueId, 1.0 / issueNumbers);
			values.put(issueId, null);
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
		lastOffert = bid;
		countOffers++;

		for (Map.Entry<Integer, Value> entry : bid.getValues().entrySet()) {
			int issueId = entry.getKey();
			Value bidValue = entry.getValue();
			// Actualizar los pesos wi
			// El peso para un atributo i únicamente debería actualizarse si el valor ofrecido
			// por el oponente en ese atributo NO ha cambiado con respecto a la última oferta
			// recibida.
			if (lastOffert.getValue(issueId) == bidValue) {
				double oldWeight = weights.get(issueId);
				weights.put(issueId, oldWeight + delta);
			}
			// Cuenta de cuántas veces ha aparecido cada
			// valor de atributo en las ofertas del oponente y
			// debera actualizarse siempre.
			Map<Value, Integer> valueCounter = values.get(issueId);
			Integer valueCount = null;
			if (valueCounter != null) {
				valueCount = valueCounter.get(bidValue);
			}
			if (valueCount != null) {
				valueCounter.put(bidValue, valueCount + 1);
			} else {
				valueCounter = new HashMap<>();
				valueCounter.put(bidValue, 1);
				values.put(issueId, valueCounter);
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
		for (Map.Entry<Integer, Map<Value, Integer>> entry : values.entrySet()) {
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
		return set;
	}

}
