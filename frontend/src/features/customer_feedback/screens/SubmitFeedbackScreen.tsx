"use client";

import React, { useState, useEffect } from "react";
import {
  Star,
  Send,
  Sparkles,
  AlertCircle,
  CheckCircle2,
  User,
  Loader2,
  ShieldCheck,
  Calendar,
  Hash,
} from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import {
  customerFeedbackService,
  type FeedbackTokenValidation,
} from "@/services/customer_feedback_service";

type SubmitFeedbackScreenProps = {
  token: string;
};

const CATEGORIES = [
  "Professionalism",
  "Responsiveness",
  "Knowledge",
  "Friendliness",
  "Communication",
  "Problem Solving",
];

/* ------------------------------------------------------------------ */
/* Reusable star rating                                                */
/* ------------------------------------------------------------------ */

function StarRating({
  label,
  description,
  value,
  onChange,
}: {
  label: string;
  description: string;
  value: number;
  onChange: (n: number) => void;
}) {
  const [hover, setHover] = useState<number | null>(null);
  const current = hover ?? value;

  const labelFor = (v: number) =>
    ["Poor", "Fair", "Good", "Great", "Excellent"][v - 1] ?? "";

  return (
    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4 py-4 first:pt-0 last:pb-0 border-b border-border/50 last:border-0">
      <div className="space-y-0.5 min-w-0">
        <label className="text-sm font-bold text-foreground block">
          {label}
        </label>
        <p className="text-xs text-muted-foreground line-clamp-2">
          {description}
        </p>
      </div>
      <div className="flex flex-col xs:flex-row xs:items-center gap-2 xs:gap-3 shrink-0">
        <div
          className="flex items-center gap-0.5"
          onMouseLeave={() => setHover(null)}
        >
          {[1, 2, 3, 4, 5].map((star) => {
            const active = star <= current;
            return (
              <button
                key={star}
                type="button"
                onClick={() => onChange(star)}
                onMouseEnter={() => setHover(star)}
                className="p-0.5 rounded-md transition-transform hover:scale-110 active:scale-95 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label={`${star} star`}
              >
                <Star
                  className={`size-5 sm:size-6 transition-colors ${active
                    ? "fill-amber-400 text-amber-400 drop-shadow-[0_2px_8px_rgba(245,158,11,0.2)]"
                    : "text-muted-foreground/30"
                    }`}
                />
              </button>
            );
          })}
        </div>
        <span className="text-[10px] sm:text-[11px] font-extrabold text-amber-600 dark:text-amber-400 bg-amber-500/10 dark:bg-amber-500/25 px-2.5 sm:px-3 py-1 rounded-full uppercase tracking-wider min-w-15 sm:min-w-19 text-center whitespace-nowrap">
          {labelFor(current)}
        </span>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Main screen                                                         */
/* ------------------------------------------------------------------ */

export function SubmitFeedbackScreen({ token }: SubmitFeedbackScreenProps) {
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [validationData, setValidationData] =
    useState<FeedbackTokenValidation | null>(null);

  const [rating, setRating] = useState(5);
  const [hoverRating, setHoverRating] = useState<number | null>(null);
  const [ratingAttitude, setRatingAttitude] = useState(5);
  const [ratingSpeed, setRatingSpeed] = useState(5);
  const [ratingAccuracy, setRatingAccuracy] = useState(5);
  const [comment, setComment] = useState("");
  const [recommendScore, setRecommendScore] = useState(10);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    async function validate() {
      try {
        const response = await customerFeedbackService.validateToken(token);
        if (response.success && response.data.valid) {
          setValidationData(response.data);
        } else {
          setErrorMsg(response.message || "Survey link is invalid or has expired.");
        }
      } catch (err: any) {
        console.error("Token validation error:", err);
        setErrorMsg(
          err?.response?.data?.message ||
          "Could not validate this feedback link.",
        );
      } finally {
        setLoading(false);
      }
    }
    validate();
  }, [token]);

  const handleCategoryToggle = (cat: string) => {
    setSelectedCategories((prev) =>
      prev.includes(cat) ? prev.filter((c) => c !== cat) : [...prev, cat],
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!comment.trim()) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await customerFeedbackService.submitByToken(token, {
        rating,
        ratingAttitude,
        ratingSpeed,
        ratingAccuracy,
        comment: comment.trim(),
        recommendScore,
      });

      if (response.success) {
        setSubmitted(true);
      } else {
        setSubmitError(
          response.message || "Submitting feedback failed. Please try again.",
        );
      }
    } catch (err: any) {
      console.error("Submission error:", err);
      setSubmitError(
        err?.response?.data?.message ||
        "An error occurred during submission. Please try again.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const getDynamicPrompt = () => {
    const currentRating = hoverRating ?? rating;
    if (currentRating >= 5)
      return "What impressed you the most about the sales staff?";
    if (currentRating === 4)
      return "What could the sales staff do to make your experience even better?";
    if (currentRating === 3) return "How could the sales staff improve next time?";
    return "We're sorry to hear that. Could you tell us what went wrong?";
  };

  const getRatingLabel = (val: number) =>
    ["Poor", "Fair", "Good", "Great", "Excellent"][val - 1] ?? "Excellent";

  const getScoreColor = (score: number, isSelected: boolean) => {
    if (isSelected) {
      if (score <= 3) return "bg-rose-600 text-white border-rose-600 shadow-md shadow-rose-500/30 scale-105";
      if (score <= 6) return "bg-amber-500 text-white border-amber-500 shadow-md shadow-amber-500/30 scale-105";
      if (score <= 8) return "bg-lime-500 text-black dark:text-white border-lime-500 shadow-md shadow-lime-500/30 scale-105";
      return "bg-emerald-600 text-white border-emerald-600 shadow-md shadow-emerald-500/30 scale-105";
    } else {
      if (score <= 3) return "bg-rose-500/5 dark:bg-rose-500/10 hover:bg-rose-500/15 border-rose-500/20 text-rose-600 dark:text-rose-400";
      if (score <= 6) return "bg-amber-500/5 dark:bg-amber-500/10 hover:bg-amber-500/15 border-amber-500/20 text-amber-600 dark:text-amber-400";
      if (score <= 8) return "bg-lime-500/5 dark:bg-lime-500/10 hover:bg-lime-500/15 border-lime-500/20 text-lime-600 dark:text-lime-400";
      return "bg-emerald-500/5 dark:bg-emerald-500/10 hover:bg-emerald-500/15 border-emerald-500/20 text-emerald-600 dark:text-emerald-400";
    }
  };

  /* --------------------------- Loading --------------------------- */
  if (loading) {
    return (
      <Card className="max-w-sm w-full p-10 flex flex-col items-center gap-4 text-center shadow-lg border-border/80">
        <Loader2 className="size-8 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">
          Loading survey details…
        </p>
      </Card>
    );
  }

  /* --------------------------- Error ---------------------------- */
  if (errorMsg) {
    return (
      <Card className="max-w-md w-full p-8 text-center space-y-5 shadow-lg border-border/80">
        <div className="mx-auto grid place-items-center size-14 rounded-full bg-destructive/10 text-destructive">
          <AlertCircle className="size-7" />
        </div>
        <div className="space-y-2">
          <h1 className="text-xl font-bold text-foreground">
            Invalid Survey Link
          </h1>
          <p className="text-sm text-muted-foreground leading-relaxed">
            {errorMsg}
          </p>
        </div>
        <div className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-muted-foreground bg-muted rounded-full px-3 py-1.5 mx-auto">
          <ShieldCheck className="size-3.5" />
          Leadora Customer Feedback Protection
        </div>
      </Card>
    );
  }

  /* --------------------------- Success -------------------------- */
  if (submitted) {
    return (
      <Card className="max-w-xl w-full p-8 sm:p-10 space-y-6 shadow-xl border-border/80">
        <div className="text-center space-y-4">
          <div className="mx-auto grid place-items-center size-16 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
            <CheckCircle2 className="size-9" />
          </div>
          <div className="space-y-2">
            <h1 className="text-2xl font-bold text-foreground">
              Thank you, {validationData?.customerName || "Valued Guest"}!
            </h1>
            <p className="text-sm text-muted-foreground leading-relaxed">
              Your feedback about our sales staff at{" "}
              {validationData?.hotelName || "Leadora"} has been shared with
              management. It helps us keep raising the bar.
            </p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-xl border border-border bg-muted/40 p-4 space-y-1.5">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              Staff Rating
            </p>
            <div className="flex items-center gap-0.5">
              {Array.from({ length: 5 }).map((_, i) => (
                <Star
                  key={i}
                  className={`size-4 ${i < rating
                    ? "fill-amber-400 text-amber-400"
                    : "text-muted-foreground/30"
                    }`}
                />
              ))}
            </div>
            <p className="text-xs font-medium text-foreground">
              {getRatingLabel(rating)}
            </p>
          </div>

          <div className="rounded-xl border border-border bg-muted/40 p-4 space-y-1.5">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              Recommendation
            </p>
            <p className="text-2xl font-bold text-foreground tabular-nums">
              {recommendScore}
              <span className="text-sm font-medium text-muted-foreground">
                {" "}
                / 10
              </span>
            </p>
            <p className="text-xs font-medium text-foreground">
              {recommendScore >= 9
                ? "Promoter"
                : recommendScore >= 7
                  ? "Passive"
                  : "Detractor"}
            </p>
          </div>
        </div>

        <div className="rounded-xl border border-border divide-y divide-border text-sm">
          {[
            ["Attitude & Friendliness", ratingAttitude],
            ["Speed & Responsiveness", ratingSpeed],
            ["Accuracy & Knowledge", ratingAccuracy],
          ].map(([label, val]) => (
            <div
              key={label as string}
              className="flex items-center justify-between px-4 py-2.5"
            >
              <span className="text-muted-foreground">{label}</span>
              <span className="font-semibold text-foreground tabular-nums">
                {val} ★
              </span>
            </div>
          ))}
        </div>

        {validationData?.bookingCode && (
          <div className="text-center text-xs text-muted-foreground">
            Booking Reference:{" "}
            <span className="font-mono font-semibold text-foreground">
              {validationData.bookingCode}
            </span>
          </div>
        )}

        <a href="/" className="w-full">
          <Button className="w-full">Return to Website</Button>
        </a>
      </Card>
    );
  }

  /* --------------------------- Form ----------------------------- */
  return (
    <div className="w-full max-w-3xl py-6 sm:py-8 lg:py-12">
      <div className="text-center space-y-2.5 mb-8">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 text-primary text-[10px] font-bold px-2.5 py-1 uppercase tracking-wider">
          <Sparkles className="size-3" />
          Customer Feedback
        </span>
        <h1 className="text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl">
          Thank you for staying with us
        </h1>
        <p className="text-sm text-muted-foreground max-w-md mx-auto">
          Your feedback about our booking consultant helps us improve the quality of our services.
        </p>
      </div>

      <Card className="w-full overflow-hidden p-6 sm:p-8 lg:p-10 shadow-xl border-border/80" hoverable={false}>
        {/* Booking & Consultant Receipt Card */}
        {validationData && (
          <div className="rounded-2xl border border-border/85 bg-muted/20 dark:bg-muted/5 p-5 mb-8 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6">
            <div className="flex items-center gap-4">
              {validationData.salesStaffAvatar ? (
                <img
                  src={validationData.salesStaffAvatar}
                  alt={validationData.salesStaffName}
                  className="size-14 rounded-full object-cover ring-2 ring-primary/20"
                />
              ) : (
                <div className="grid place-items-center size-14 rounded-full bg-muted text-muted-foreground">
                  <User className="size-7" />
                </div>
              )}
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-1.5">
                  <p className="text-[10px] font-bold uppercase tracking-wide text-muted-foreground">
                    Your Sales Staff
                  </p>
                  <span className="inline-flex items-center gap-0.5 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-[10px] font-bold px-1.5 py-0.5">
                    <CheckCircle2 className="size-2.5" /> Verified
                  </span>
                </div>
                <p className="text-base font-bold text-foreground">
                  {validationData.salesStaffName}
                </p>
                <p className="text-xs text-muted-foreground">
                  Sales Staff • {validationData.hotelName || "Leadora Resort"}
                </p>
              </div>
            </div>

            <div className="flex flex-wrap sm:flex-col items-start gap-3 sm:gap-1 text-xs text-muted-foreground border-t sm:border-t-0 sm:border-l border-border pt-4 sm:pt-0 sm:pl-6 w-full sm:w-auto">
              <div className="flex items-center gap-2">
                <Hash className="size-3.5 text-muted-foreground shrink-0" />
                <span>
                  Code:{" "}
                  <span className="font-mono font-semibold text-foreground">
                    {validationData.bookingCode}
                  </span>
                </span>
              </div>
              {validationData.checkOutDate && (
                <div className="flex items-center gap-2">
                  <Calendar className="size-3.5 text-muted-foreground shrink-0" />
                  <span>
                    Check-out:{" "}
                    <span className="font-semibold text-foreground">
                      {validationData.checkOutDate}
                    </span>
                  </span>
                </div>
              )}
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6 sm:space-y-8">
          {/* Overall */}
          <div className="flex flex-col items-center justify-center text-center space-y-3">
            <label className="text-sm font-semibold text-foreground">
              How satisfied were you with the sales staff's support?
            </label>
            <div
              className="flex items-center justify-center gap-2 w-full"
              onMouseLeave={() => setHoverRating(null)}
            >
              <div className="flex items-center gap-1 sm:gap-1.5">
                {[1, 2, 3, 4, 5].map((star) => {
                  const active = star <= (hoverRating ?? rating);
                  return (
                    <button
                      key={star}
                      type="button"
                      onClick={() => setRating(star)}
                      onMouseEnter={() => setHoverRating(star)}
                      className="p-1 rounded-md transition-transform hover:scale-110 active:scale-95 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      aria-label={`${star} star`}
                    >
                      <Star
                        className={`size-6 sm:size-8 transition-colors ${active
                          ? "fill-amber-400 text-amber-400"
                          : "text-muted-foreground/30"
                          }`}
                      />
                    </button>
                  );
                })}
              </div>
              <span className="ml-2 text-sm font-semibold text-foreground min-w-17.5 text-left">
                {getRatingLabel(hoverRating ?? rating)}
              </span>
            </div>
          </div>

          {/* NPS */}
          <div className="space-y-3">
            <div className="flex flex-col xs:flex-row xs:items-start xs:justify-between gap-2 xs:gap-3">
              <label className="text-sm font-semibold text-foreground">
                How likely are you to recommend this sales staff?
              </label>
              <span className="shrink-0 rounded-full bg-primary/10 text-primary text-xs font-bold px-2.5 py-1 tabular-nums whitespace-nowrap">
                {recommendScore}/10
              </span>
            </div>
            <div className="grid grid-cols-11 gap-0.5 sm:gap-1.5 w-full">
              {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((score) => {
                const isSelected = recommendScore === score;
                return (
                  <button
                    key={score}
                    type="button"
                    onClick={() => setRecommendScore(score)}
                    className={`h-9 w-full rounded-lg text-[10px] sm:text-xs font-bold border transition-all duration-200 flex items-center justify-center focus:outline-none focus-visible:ring-2 focus-visible:ring-ring ${getScoreColor(score, isSelected)}`}
                  >
                    {score}
                  </button>
                );
              })}
            </div>
            <div className="flex justify-between text-[10px] xs:text-[11px] text-muted-foreground">
              <span>Not likely</span>
              <span>Extremely likely</span>
            </div>
          </div>

          {/* Detailed Evaluation */}
          <div className="space-y-3 sm:space-y-4 pt-4 sm:pt-6 border-t border-border">
            <div className="leading-tight">
              <h3 className="text-sm font-semibold text-foreground">
                Detailed Rating
              </h3>
              <p className="text-xs text-muted-foreground mt-0.5">
                Please rate specific aspects of your experience
              </p>
            </div>
            <div className="rounded-xl border border-border/80 bg-muted/20 dark:bg-muted/5 p-3 sm:p-4 lg:p-5 space-y-1">
              <StarRating
                label="Attitude & Friendliness"
                description="Was the sales staff polite, professional, and welcoming?"
                value={ratingAttitude}
                onChange={setRatingAttitude}
              />
              <StarRating
                label="Speed & Responsiveness"
                description="How quickly did they reply and process your request?"
                value={ratingSpeed}
                onChange={setRatingSpeed}
              />
              <StarRating
                label="Accuracy & Knowledge"
                description="Were their answers clear, accurate, and professional?"
                value={ratingAccuracy}
                onChange={setRatingAccuracy}
              />
            </div>
          </div>

          {/* Categories */}
          <div className="space-y-2.5">
            <label className="text-sm font-semibold text-foreground">
              What stood out?{" "}
              <span className="text-xs font-normal text-muted-foreground">
                (optional)
              </span>
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {CATEGORIES.map((cat) => {
                const isSelected = selectedCategories.includes(cat);
                return (
                  <button
                    key={cat}
                    type="button"
                    onClick={() => handleCategoryToggle(cat)}
                    className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-all whitespace-nowrap ${isSelected
                      ? "bg-primary text-primary-foreground border-primary shadow-sm"
                      : "bg-muted/50 border-border text-muted-foreground hover:text-foreground hover:bg-muted"
                      }`}
                  >
                    {cat}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Comment */}
          <div className="space-y-2">
            <label
              htmlFor="feedback-comment"
              className="text-sm font-semibold text-foreground"
            >
              {getDynamicPrompt()}
            </label>
            <textarea
              id="feedback-comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={4}
              placeholder="Share the details of your experience…"
              className="w-full rounded-xl border border-border bg-muted/40 p-3.5 text-sm text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-primary focus:bg-background focus:ring-4 focus:ring-primary/10 transition resize-none leading-relaxed"
            />
          </div>

          {submitError && (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-xs font-medium text-destructive"
            >
              <AlertCircle className="size-4 shrink-0 mt-px" />
              <span>{submitError}</span>
            </div>
          )}

          <Button
            type="submit"
            variant="primary"
            disabled={submitting || !comment.trim()}
            size="lg"
            isLoading={submitting}
            rightIcon={!submitting ? <Send size={16} /> : undefined}
            className="w-full mt-2"
          >
            {submitting ? "Submitting…" : "Submit Feedback"}
          </Button>
        </form>
      </Card>
    </div>
  );
}
