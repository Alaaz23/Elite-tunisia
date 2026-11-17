import { HttpErrorResponse } from '@angular/common/http';
import { ApiHttpError } from '../../core/models/api-http-error';
import { GuaranteeRequest, PartnerRequest, PartnerType } from '../../core/services/partner-api.service';

export const GUARANTEE_MAX_DAYS = 90;

export interface PartnerFormValue {
  partnerType: PartnerType;
  name: string;
  email: string;
  phone: string;
  address: string;
  city: string;
  taxIdentifier: string;
  userId: string;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const BACKEND_LABELS: Record<string, string> = {
  PARTNER_NAME_REQUIRED: 'Le nom ou la raison sociale est obligatoire.',
  PARTNER_TAX_IDENTIFIER_REQUIRED: 'Le matricule fiscal est obligatoire pour une société.',
  PARTNER_TAX_IDENTIFIER_NOT_ALLOWED: 'Le matricule fiscal n’est pas autorisé pour un particulier.',
  PARTNER_INACTIVE: 'Ce partenaire est désactivé.',
  PARTNER_NOT_FOUND: 'Partenaire introuvable.',
  USER_NOT_FOUND: 'Compte utilisateur introuvable.',
  USER_NOT_PARTNER_ROLE: 'Le compte choisi n’a pas le rôle PARTNER.',
  USER_ALREADY_LINKED_TO_PARTNER: 'Ce compte est déjà rattaché à un autre partenaire.',
  VEHICLE_NOT_FOUND: 'Véhicule introuvable.',
  VEHICLE_ALREADY_ASSIGNED_TO_PARTNER: 'Ce véhicule est déjà affecté à ce partenaire.',
  VEHICLE_TRANSFER_BLOCKED_BY_GUARANTEE: 'Le transfert est refusé : une garantie active existe pour ce véhicule.',
  VEHICLE_TRANSFER_BLOCKED_BY_BOOKING: 'Le transfert est refusé : une réservation bloquante existe pour ce véhicule.',
  VEHICLE_HAS_NO_PARTNER: 'Ce véhicule n’a pas de partenaire affecté.',
  VEHICLE_ARCHIVED: 'Une garantie ne peut pas être créée sur un véhicule archivé.',
  GUARANTEE_PERIOD_INVALID: 'La date de fin doit être postérieure à la date de début.',
  GUARANTEE_START_IN_PAST: 'La garantie doit commencer dans le futur.',
  GUARANTEE_TOO_LONG: `La garantie ne peut pas dépasser ${GUARANTEE_MAX_DAYS} jours.`,
  GUARANTEE_OVERLAP: 'Une garantie active chevauche déjà cette période.',
  GUARANTEE_NOT_FOUND: 'Garantie introuvable.',
  GUARANTEE_NOT_ACTIVE: 'Cette garantie n’est plus active.',
  GUARANTEE_EXPIRED: 'Cette garantie est expirée.',
  GUARANTEE_HAS_BOOKINGS: 'Cette garantie est liée à une réservation et ne peut pas être retirée.',
};

export function partnerFormErrors(value: PartnerFormValue): string[] {
  const errors: string[] = [];
  const name = value.name.trim();
  const tax = value.taxIdentifier.trim();
  const email = value.email.trim();
  if (!name) errors.push('Le nom ou la raison sociale est obligatoire.');
  if (name.length > 160) errors.push('Le nom ne peut pas dépasser 160 caractères.');
  if (value.partnerType === 'COMPANY' && !tax) errors.push('Le matricule fiscal est obligatoire pour une société.');
  if (value.partnerType === 'INDIVIDUAL' && tax) errors.push('Le matricule fiscal n’est pas autorisé pour un particulier.');
  if (tax.length > 40) errors.push('Le matricule fiscal ne peut pas dépasser 40 caractères.');
  if (email && !EMAIL_PATTERN.test(email)) errors.push('L’adresse e-mail est invalide.');
  if (value.phone.trim().length > 40) errors.push('Le téléphone ne peut pas dépasser 40 caractères.');
  if (value.address.trim().length > 255) errors.push('L’adresse ne peut pas dépasser 255 caractères.');
  if (value.city.trim().length > 100) errors.push('La ville ne peut pas dépasser 100 caractères.');
  if (value.userId.trim() && !UUID_PATTERN.test(value.userId.trim())) errors.push('L’identifiant du compte doit être un UUID.');
  return errors;
}

export function toPartnerRequest(value: PartnerFormValue): PartnerRequest {
  const optional = (text: string) => text.trim() || null;
  return {
    partnerType: value.partnerType,
    name: value.name.trim(),
    email: optional(value.email),
    phone: optional(value.phone),
    address: optional(value.address),
    city: optional(value.city),
    taxIdentifier: value.partnerType === 'COMPANY' ? optional(value.taxIdentifier) : null,
    userId: optional(value.userId),
  };
}

export function guaranteeFormErrors(vehicleId: string, startsAt: string, endsAt: string, reason: string, now: Date = new Date()): string[] {
  const errors: string[] = [];
  if (!vehicleId.trim()) errors.push('L’identifiant du véhicule est obligatoire.');
  const start = new Date(startsAt);
  const end = new Date(endsAt);
  if (!startsAt || Number.isNaN(start.getTime())) errors.push('La date de début est invalide.');
  if (!endsAt || Number.isNaN(end.getTime())) errors.push('La date de fin est invalide.');
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return errors;
  if (end.getTime() <= start.getTime()) errors.push('La date de fin doit être postérieure à la date de début.');
  if (end.getTime() - start.getTime() > GUARANTEE_MAX_DAYS * 24 * 60 * 60 * 1000) errors.push(`La garantie ne peut pas dépasser ${GUARANTEE_MAX_DAYS} jours.`);
  if (start.getTime() < now.getTime()) errors.push('La garantie doit commencer dans le futur.');
  const trimmedReason = reason.trim();
  if (!trimmedReason) errors.push('Le motif est obligatoire.');
  if (trimmedReason.length > 500) errors.push('Le motif ne peut pas dépasser 500 caractères.');
  return errors;
}

export function toGuaranteeRequest(vehicleId: string, startsAt: string, endsAt: string, reason: string): GuaranteeRequest {
  return {
    vehicleId: vehicleId.trim(),
    startsAt: new Date(startsAt).toISOString(),
    endsAt: new Date(endsAt).toISOString(),
    reason: reason.trim(),
  };
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  const raw = error instanceof ApiHttpError ? error.message : error instanceof HttpErrorResponse ? (error.error?.detail ?? error.error?.code ?? '') : '';
  return BACKEND_LABELS[raw] ?? (raw && !/^[A-Z_]+$/.test(raw) ? raw : fallback);
}
