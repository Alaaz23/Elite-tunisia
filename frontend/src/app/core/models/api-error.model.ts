export interface ApiProblem {
  status?: number;
  detail?: string;
  fieldErrors?: Record<string, string>;
}